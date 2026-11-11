package trade.gozero.pearl;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Read-only sensors: prefer CPU/GPU die zones to vendor AP estimates. */
final class CpuTemperature {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"cpu-temperature");t.setDaemon(true);return t;});
    private static final ScheduledExecutorService TIMEOUT=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"temperature-timeout");t.setDaemon(true);return t;});
    private static final Pattern HAL=Pattern.compile("Temperature\\{mValue=([^,]+),\\s*mType=(\\d+),\\s*mName=([^,}]+),");
    static final long FRESH_MS=4000;
    static final class Sample {
        final Map<String,Float> cores,gpus;final long time;final String cpuSource,gpuSource;
        Sample(Map<String,Float> cores,Map<String,Float> gpus,long time,String cpuSource,String gpuSource){
            this.cores=Collections.unmodifiableMap(new TreeMap<>(cores));this.gpus=Collections.unmodifiableMap(new TreeMap<>(gpus));
            this.time=time;this.cpuSource=cpuSource;this.gpuSource=gpuSource;
        }
        float maximum(){return maximumOf(cores);}
        float gpuMaximum(){return maximumOf(gpus);}
        boolean fresh(long now){return !cores.isEmpty()&&inTime(now);}
        boolean gpuFresh(long now){return !gpus.isEmpty()&&inTime(now);}
        private boolean inTime(long now){return now>=time&&now-time<=FRESH_MS;}
        String cpuLabel(long now){return fresh(now)?cpuSource:cores.isEmpty()?cpuSource:"sensor_stale";}
        String gpuLabel(long now){return gpuFresh(now)?gpuSource:gpus.isEmpty()?gpuSource:"sensor_stale";}
        private static float maximumOf(Map<String,Float> values){float max=Float.NaN;for(float v:values.values())if(Float.isNaN(max)||v>max)max=v;return max;}
    }
    private static volatile Sample latest=new Sample(Collections.emptyMap(),Collections.emptyMap(),0,"sensor_wait","sensor_wait");
    private static boolean pending;
    private static long lastRequest=-1000;
    private static List<File> zones;
    private static boolean cpuDirectSeen,gpuDirectSeen;
    static Sample snapshot(){return latest;}
    static int kind(String type){
        String t=type.trim().toLowerCase(Locale.ROOT);
        if(t.matches("cpu(?:[0-9]+|[-_][0-9]+(?:[-_][0-9]+)*|ss(?:[-_][0-9]+)?)"))return 0;
        if(t.matches("gpu(?:[0-9]+|[-_][0-9]+|ss(?:[-_][0-9]+)?)"))return 1;
        return -1;
    }
    static float milliCelsius(String raw){
        try{float v=Float.parseFloat(raw.trim())/1000f;return Float.isFinite(v)&&v>0&&v<=150?v:Float.NaN;}catch(Exception e){return Float.NaN;}
    }
    static Map<String,Float> parseCurrent(String raw){return parseHal(raw,0);}
    static Map<String,Float> parseHal(String raw,int type){
        TreeMap<String,Float> values=new TreeMap<>();int start=raw.indexOf("Current temperatures from HAL:");if(start<0)return values;
        String current=raw.substring(start);
        for(String end:new String[]{"Current cooling devices","Temperature static thresholds","Temperature thresholds","Cached temperatures","HAL connection:"}){int i=current.indexOf(end);if(i>=0)current=current.substring(0,i);}
        Matcher m=HAL.matcher(current);
        while(m.find())try{float v=Float.parseFloat(m.group(1).trim());if(Integer.parseInt(m.group(2))==type&&Float.isFinite(v)&&v>0&&v<=150)values.put(m.group(3).trim(),v);}catch(NumberFormatException ignored){}
        return values;
    }
    private static String read(File f)throws IOException{try(BufferedReader r=new BufferedReader(new FileReader(f))){String s=r.readLine();return s==null?"":s;}}
    private static void sysfs(Map<String,Float> cpu,Map<String,Float> gpu){
        if(zones==null){
            zones=new ArrayList<>();
            // Some vendors deny directory enumeration but allow individual read-only zones.
            for(int i=0;i<256;i++){File zone=new File("/sys/class/thermal/thermal_zone"+i);try{if(kind(read(new File(zone,"type")))>=0)zones.add(zone);}catch(IOException|SecurityException ignored){}}
        }
        for(File zone:zones)try{
            String type=read(new File(zone,"type"));int k=kind(type);float v=milliCelsius(read(new File(zone,"temp")));
            if(Float.isFinite(v)&&k>=0)(k==0?cpu:gpu).put(type,v);
        }catch(IOException|SecurityException ignored){}
    }
    static synchronized void request(Context context){
        long now=SystemClock.elapsedRealtime();if(pending||now-lastRequest<1000)return;lastRequest=now;pending=true;
        boolean dump=context.checkSelfPermission(Manifest.permission.DUMP)==PackageManager.PERMISSION_GRANTED;
        IO.execute(()->{
            Map<String,Float> cpu=new TreeMap<>(),gpu=new TreeMap<>();String cs="sensor_unavailable",gs="sensor_unavailable";Process p=null;ScheduledFuture<?> timeout=null;
            long sampled=SystemClock.elapsedRealtime();
            try{
                sysfs(cpu,gpu);if(!cpu.isEmpty()){cs="sensor_direct";cpuDirectSeen=true;}if(!gpu.isEmpty()){gs="sensor_direct";gpuDirectSeen=true;}
                if(dump&&((cpu.isEmpty()&&!cpuDirectSeen)||(gpu.isEmpty()&&!gpuDirectSeen))){
                    p=new ProcessBuilder("/system/bin/dumpsys","-t","1","thermalservice").redirectErrorStream(true).start();
                    Process bounded=p;timeout=TIMEOUT.schedule(bounded::destroyForcibly,2,TimeUnit.SECONDS);
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>65536)throw new IOException("Thermal output too large");out.write(b,0,n);}}
                    if(p.waitFor(1,TimeUnit.SECONDS)&&p.exitValue()==0){
                        String raw=new String(out.toByteArray(),StandardCharsets.UTF_8);
                        // Never silently replace a lost die sensor with a lower AP estimate.
                        if(cpu.isEmpty()&&!cpuDirectSeen){cpu.putAll(parseHal(raw,0));if(!cpu.isEmpty())cs=cpu.keySet().stream().allMatch(n->kind(n)==0)?"sensor_hal":"sensor_ap";}
                        if(gpu.isEmpty()&&!gpuDirectSeen){gpu.putAll(parseHal(raw,1));if(!gpu.isEmpty())gs="sensor_hal";}
                    }
                }
            }catch(Exception ignored){}finally{
                if(p!=null&&p.isAlive())p.destroyForcibly();
                if(timeout!=null)timeout.cancel(false);
                synchronized(CpuTemperature.class){latest=new Sample(cpu,gpu,sampled,cs,gs);pending=false;}
            }
        });
    }
}
