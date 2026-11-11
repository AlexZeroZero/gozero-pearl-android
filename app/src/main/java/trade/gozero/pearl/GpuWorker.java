package trade.gozero.pearl;

import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.Process;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Optional, isolated OpenCL worker. Failure never terminates the CPU worker. */
final class GpuWorker {
    private final Context context;
    private final SharedPreferences prefs;
    private GpuProfile profile;
    final ShareTotals shares=new ShareTotals();
    private Process child;
    private volatile int childPid;
    private boolean suspended;
    private long suspendedAt;
    private Thread reader;
    private volatile boolean proofPass;
    private boolean checking,disabled,disposed,verified;
    private long checkDeadline;
    private int port;
    private File kernels;
    double rate,average;
    int rssMiB;
    boolean connected;
    String status="gpu_wait";
    GpuWorker(Context context){
        this.context=context;this.prefs=context.getSharedPreferences("app",0);
        profile=GpuProfile.forSoc(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"");
        verified=key().equals(prefs.getString("verifiedGpu",""));
    }
    private String key(){return profile.verificationKey(GpuBuild.ID,Build.FINGERPRINT);}
    synchronized boolean usable(){return !disabled&&!disposed;}
    synchronized void tick(String blockedReason,String mode,String pool,String wallet,String worker){
        if(disposed||disabled)return;
        if(!blockedReason.isEmpty()){hold(blockedReason);return;}
        try{
            if(suspended){
                if(!ProcessControl.resume(child,childPid))throw new IOException("GPU resume failed");
                if(checking)checkDeadline+=SystemClock.elapsedRealtime()-suspendedAt;
                suspended=false;State.log("[GPU] Resumed retained OpenCL context");
            }
            if(child!=null&&!child.isAlive()){
                int code=child.exitValue();joinReader();child=null;rate=average=0;rssMiB=0;connected=false;
                if(checking){
                    if(code!=0||!proofPass)throw new IOException("GPU V3 self-test failed ("+code+")");
                    verified=true;prefs.edit().putString("verifiedGpu",key()).apply();
                    State.log("[GPU] PASS: V3 proof verified locally on this device");
                }else{shares.nextProcess();throw new IOException("GPU worker exited ("+code+")");}
            }
            if(child==null){
                if(kernels==null)extractKernels();
                checking=!verified;proofPass=false;
                ArrayList<String> a=new ArrayList<>(Arrays.asList("--algo","pearl","--backend","opencl","--threads","1","--m","4","--n","4","--ocl-tile",profile.tile,"--ocl-dot","off","--ocl-issue","broadcast","--ocl-cpm-type","float","--batch-size",String.valueOf(profile.batch),"--verify","--agent","GozeroPearl/1.0"));
                if(checking){Collections.addAll(a,"--mock","--mock-diff","30");checkDeadline=SystemClock.elapsedRealtime()+120000;status="gpu_checking";}
                else{
                    try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
                    Collections.addAll(a,"--api-port",String.valueOf(port),"--api-bind","127.0.0.1");
                    if("bench".equals(mode))Collections.addAll(a,"--mock","--mock-diff","90");
                    else Collections.addAll(a,"--pool",pool,"--wallet",wallet,"--worker",MiningConfig.gpuWorker(worker));
                    status="gpu_starting";
                }
                a.add(0,context.getApplicationInfo().nativeLibraryDir+"/libpearlgpu.so");
                boolean sharedProfile=CpuSchedulingProfile.spread(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"",prefs.getBoolean("autoCpu",false));
                String hostMask=sharedProfile?ThermalCpuSet.cooler(3):ThermalCpuSet.withoutPrime();
                ProcessBuilder p=new ProcessBuilder(ProcessControl.command(ThermalCpuSet.apply(a,hostMask))).directory(context.getFilesDir()).redirectErrorStream(true);
                p.environment().put("CP_OCL_KERNEL_DIR",kernels.getAbsolutePath());
                p.environment().put("CP_VULKAN_SCAN",profile.vulkan?"1":"0");
                p.environment().put("LD_LIBRARY_PATH","/vendor/lib64:/system/lib64");
                p.environment().put("OMP_WAIT_POLICY","PASSIVE");p.environment().put("KMP_BLOCKTIME","0");p.environment().put("RAYON_NUM_THREADS","1");
                p.environment().put("OMP_NUM_THREADS","1");p.environment().put("OMP_THREAD_LIMIT","1");
                childPid=0;child=p.start();startReader(child);
                State.log((checking?"[GPU] Checking signed core + V3 proof":"[GPU] Auxiliary worker started · 4K × 4K · separate pool worker")+" · "+(profile.vulkan?"Vulkan INT8":"OpenCL")+" · "+profile.tile+" · batch "+profile.batch);
            }
            if(checking){if(SystemClock.elapsedRealtime()>checkDeadline)throw new IOException("GPU self-test timeout");}
            else{
                try{
                    NativeStats s=NativeStats.read(port);rate=s.rate;average=s.average;rssMiB=s.rssMiB;connected=s.connected;shares.update(s.accepted,s.rejected);
                    status="bench".equals(mode)||connected?"gpu_running":"gpu_starting";
                }catch(Exception e){rate=average=0;connected=false;status="gpu_starting";}
            }
        }catch(Exception e){
            pause();
            if(profile.vulkan){
                profile=GpuProfile.openClForSoc(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"");
                verified=false;checking=false;proofPass=false;status="gpu_checking";
                State.log("[GPU] Vulkan unavailable: "+e.getMessage()+"; falling back to OpenCL with a fresh V3 proof check");
            }else{disabled=true;status="gpu_failed";State.log("[GPU] Disabled for this session: "+e.getMessage()+"; CPU continues");}
        }
    }
    private void extractKernels()throws IOException{
        File dir=new File(context.getFilesDir(),"opencl-kernels");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Cannot create kernel directory");
        String[] names=context.getAssets().list("kernels");if(names==null||names.length==0)throw new IOException("Missing kernel assets");
        for(String name:names){
            if(!name.matches("[a-zA-Z0-9_]+\\.cl"))throw new IOException("Invalid kernel asset name");
            try(InputStream in=context.getAssets().open("kernels/"+name);OutputStream out=new FileOutputStream(new File(dir,name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        }
        kernels=dir;
    }
    private void startReader(Process p){
        reader=new Thread(()->{
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){int pid=ProcessControl.pid(line);if(pid>0){if(child==p)childPid=pid;continue;}State.log("[GPU] "+line);if(line.contains("[mock] PASS:"))proofPass=true;}}
            catch(IOException ignored){}
        },"pearl-gpu-output");reader.start();
    }
    private void joinReader(){try{if(reader!=null)reader.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}reader=null;}
    synchronized void pause(){
        if(child!=null){
            if(!checking&&!suspended)try{NativeStats s=NativeStats.read(port);shares.update(s.accepted,s.rejected);}catch(Exception ignored){}
            if(suspended){ProcessControl.kill(child,childPid);child.destroyForcibly();}else child.destroy();try{if(!child.waitFor(350,TimeUnit.MILLISECONDS)){ProcessControl.kill(child,childPid);child.destroyForcibly().waitFor(350,TimeUnit.MILLISECONDS);}}catch(InterruptedException e){child.destroyForcibly();Thread.currentThread().interrupt();}
            child=null;joinReader();if(!checking)shares.nextProcess();
            State.log("[GPU] Worker stopped; auxiliary rate cleared");
        }
        suspended=false;childPid=0;rate=average=0;rssMiB=0;connected=false;
    }
    synchronized void close(){disposed=true;pause();status="gpu_off";}
    synchronized void hold(String reason){
        if(disabled||disposed)return;
        if("gpu_pause_memory".equals(reason))pause();
        else if(child!=null&&!suspended){
            if(!checking)try{NativeStats s=NativeStats.read(port);shares.update(s.accepted,s.rejected);}catch(Exception ignored){}
            if(ProcessControl.suspend(child,childPid)){suspended=true;suspendedAt=SystemClock.elapsedRealtime();State.log("[GPU] Paused; OpenCL context retained");}else pause();
        }
        rate=average=0;connected=false;
        if(!reason.equals(status))State.log("[GPU] "+Words.t(context,reason));status=reason;
    }
}
