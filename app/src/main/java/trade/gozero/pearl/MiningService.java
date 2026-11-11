package trade.gozero.pearl;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.Process;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public final class MiningService extends Service {
    static final String STOP="trade.gozero.pearl.STOP";
    static final String CORE=CoreBuild.ID;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean stopping;
    private volatile Process child;
    private volatile int childPid;
    private boolean cpuSuspended;
    private boolean spreadCpu;
    private int writtenDuty=-1;
    private String thermalMask="",launchedMask="";
    private PowerManager.WakeLock wake;
    private int port,cpuRss;
    private double cpuRate,cpuAverage;
    private boolean cpuConnected;
    private final ShareTotals cpuShares=new ShareTotals();
    private final CpuWorkerThreads cpuThreads=new CpuWorkerThreads();
    private volatile GpuWorker gpu;
    private Thread reader;
    private boolean proofPass;
    private SharedPreferences prefs(){return getSharedPreferences("app",0);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"GozeroPearl:compute");
        wake.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(STOP.equals(intent.getAction())){stopping=true;kill();closeGpu();if(!State.active)stopSelf();return START_NOT_STICKY;}
        if(State.active)return START_NOT_STICKY;
        String mode=intent.getStringExtra("mode");if(!"bench".equals(mode)&&!"check".equals(mode))mode="mine";
        if("mine".equals(mode)&&!MiningConfig.address(prefs().getString("wallet",""))){State.status="needwallet";stopSelf();return START_NOT_STICKY;}
        if("mine".equals(mode)&&PoolProvider.from(prefs().getString("pool",""))==PoolProvider.PEARLHASH){State.status="pool_protocol";stopSelf();return START_NOT_STICKY;}
        closeGpu();gpu=null;stopping=false;proofPass=false;cpuShares.reset();cpuRate=cpuAverage=0;cpuRss=0;cpuConnected=false;State.reset(mode);
        State.started=SystemClock.elapsedRealtime();startForeground(101,notification());
        final String task=mode;executor.execute(()->runTask(task));
        return START_NOT_STICKY;
    }
    private Notification notification(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("mining",Words.t(this,"home"),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MiningService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"mining").setSmallIcon(R.drawable.ic_pearl).setContentTitle("Gozero Pearl · "+String.format(Locale.US,"%.2f GH/s",State.rate/1e9))
            .setContentText(Words.t(this,State.status)+" · "+State.threads+" CPU · "+State.rssMiB+" MiB")
            .setContentIntent(open).addAction(new Notification.Action.Builder(null,Words.t(this,"stop"),stop).build()).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void runTask(String mode){
        boolean benchmark="bench".equals(mode),check="check".equals(mode);
        long deadline=State.started+(benchmark?30000:check?120000:Long.MAX_VALUE/2);
        ThermalPolicy thermal=new ThermalPolicy();
        GpuPolicy gpuThermal=new GpuPolicy();
        CpuLoadPolicy cpuLoad=new CpuLoadPolicy();
        CpuPacingPolicy cpuPacing=new CpuPacingPolicy();writtenDuty=-1;
        int retries=0;thermalMask=launchedMask="";
        int thermalTier=0;boolean cpuWasHot=false;
        long stableSince=-1;String stableLoad="",savedTune="";
        try{
            int threads=Math.max(1,Math.min(Runtime.getRuntime().availableProcessors(),prefs().getInt("threads",4)));
            String matrix=MiningConfig.matrix(prefs().getString("matrix",""),prefs().getInt("size",8));
            State.threads=threads;State.threadLimit=threads;State.autoCpu=prefs().getBoolean("autoCpu",false);State.matrix=matrix;
            spreadCpu=CpuSchedulingProfile.spread(Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:"",State.autoCpu)&&!check;
            if(!check&&prefs().getBoolean("gpu",false))gpu=new GpuWorker(this);
            if(spreadCpu){
                thermalMask=ThermalCpuSet.withoutPrime();
                // 0.9 cached a low-power fallback as if it were the fastest profile.
                // Invalidate it without changing wallet, pool, limits or user ceiling.
                prefs().edit().remove("cpuTuneKey").remove("cpuTuneThreads").remove("cpuTuneTier").remove("cpuTuneTime").apply();
                writeDuty(40);
                State.log("[CPU] SM8650 capacity-aware scheduling: "+CpuSchedulingProfile.spreadThreads(threads)+" threads, mask="+thermalMask+", paced tier="+ThermalCpuSet.pacingMask());
            }else if(!check&&State.autoCpu&&gpu!=null){thermalTier=1;thermalMask=ThermalCpuSet.cooler(thermalTier);State.log("[CPU] Shared GPU load: initial CPU mask="+(thermalMask.isEmpty()?"system default (capacity unavailable)":thermalMask));}
            State.verified=verificationKey(matrix).equals(prefs().getString("verifiedCore",""));
            String pool=MiningConfig.pool(prefs().getString("pool","pearl-cpu-eu1.luckypool.io:3370"));
            String wallet=prefs().getString("wallet","").trim().toLowerCase(Locale.ROOT);
            String worker=prefs().getString("worker",MainActivity.defaultWorker());if(!MiningConfig.worker(worker))throw new IllegalArgumentException("Invalid worker name");
            int cpuLimit=Math.max(65,Math.min(92,prefs().getInt("cpuLimit",92)));
            int batLimit=Math.max(35,Math.min(45,prefs().getInt("batLimit",43)));
            String tuneKey=CORE+":"+GpuBuild.ID+":"+Build.FINGERPRINT+":"+matrix+":"+threads+":"+cpuLimit+":"+batLimit;
            long tuneAge=System.currentTimeMillis()-prefs().getLong("cpuTuneTime",0);
            int tuneThreads=prefs().getInt("cpuTuneThreads",0),tuneTier=prefs().getInt("cpuTuneTier",0);
            if(!spreadCpu&&State.autoCpu&&gpu!=null&&tuneKey.equals(prefs().getString("cpuTuneKey",""))&&tuneAge>=0&&tuneAge<86400000L&&tuneThreads>0&&tuneThreads<=threads&&tuneTier>=1&&tuneTier<=3){
                String mask=ThermalCpuSet.cooler(tuneTier);
                if(!mask.isEmpty()){thermalTier=tuneTier;thermalMask=mask;cpuLoad.restore(tuneThreads);State.log("[CPU] Reusing stable automatic profile: "+tuneThreads+" threads, mask="+mask);}
            }
            State.log("Gozero Pearl 1.0 · "+mode+" · "+threads+" threads · "+MiningConfig.matrixLabel(matrix));
            if(!check&&!State.verified){State.log("Running SIMD parity and V3 proof checks before work");State.status="checking";selfTest(threads,matrix);if(stopping)return;}
            if(check){State.status="checking";selfTest(threads,matrix);if(!stopping)State.status="done";return;}
            if(benchmark)deadline=SystemClock.elapsedRealtime()+30000;
            while(!stopping){
                long now=SystemClock.elapsedRealtime();State.elapsed=now-State.started;
                if(now>=deadline){
                    State.lastBenchmark=State.average;prefs().edit().putFloat("lastBenchmark",(float)State.lastBenchmark).apply();
                    State.log("Offline benchmark finished: "+String.format(Locale.US,"%.2f GMAC/s (window average)",State.lastBenchmark/1e9));State.status="done";break;
                }
                sampleTemperatures();
                Intent bat=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                boolean powered=bat!=null&&bat.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
                PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
                int level=Build.VERSION.SDK_INT>=29?pm.getCurrentThermalStatus():0;
                if(spreadCpu)writeDuty(cpuPacing.update(State.cpu,level,cpuLimit,now));
                int activeThreads=spreadCpu?CpuSchedulingProfile.spreadThreads(threads):State.autoCpu?cpuLoad.threads(threads,State.cpu,State.battery,level,cpuLimit,batLimit,now):threads;
                boolean cpuHot=Float.isFinite(State.cpu)&&State.cpu>=cpuLimit;
                // If even one performance CPU overheats, leave GPU headroom
                // using the efficiency tier. This is only an automatic GPU session.
                int maxTier=gpu!=null&&gpu.usable()&&activeThreads==1?3:2;
                if(thermalTier==3&&gpu!=null&&!gpu.usable()){thermalTier=2;thermalMask=ThermalCpuSet.cooler(2);State.log("[CPU] GPU unavailable; restoring performance CPU tier");}
                if(!spreadCpu&&State.autoCpu&&cpuHot&&!cpuWasHot&&thermalTier<maxTier){
                    thermalTier++;String nextMask=ThermalCpuSet.cooler(thermalTier);
                    if(!nextMask.isEmpty()&&!nextMask.equals(thermalMask)){thermalMask=nextMask;State.log("[CPU] Thermal recovery CPU mask="+thermalMask+" · capacity tier "+thermalTier);}
                }
                cpuWasHot=cpuHot;
                // Observe GPU heat even while the CPU is paused, retaining its
                // hysteresis instead of treating CPU resume as GPU permission.
                boolean gpuCool=gpuThermal.allow(State.cpu,State.gpuTemperature,State.battery,level,cpuLimit,batLimit,now);
                String pause=null;
                if(thermal.pause(State.cpu,State.battery,level,cpuLimit,batLimit,now))pause="cooling";
                else if(prefs().getBoolean("chargeOnly",true)&&!powered)pause="charging";
                ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mem);
                if(pause==null&&(mem.lowMemory||(child==null&&mem.availMem<(MiningConfig.memoryBudgetMiB(matrix)+256L)*1048576L)))pause="memory";
                if(pause!=null){
                    if(benchmark)throw new IOException("Benchmark interrupted: "+pause);
                    if(child!=null&&!cpuSuspended){
                        readStats();
                        if("cooling".equals(pause)&&ProcessControl.suspend(child,childPid)){cpuSuspended=true;State.computing=false;State.log("[CPU] Thermal pause; matrix state retained");}
                        else{kill();cpuShares.nextProcess();}
                        if(wake.isHeld())wake.release();
                    }
                    if(child!=null&&cpuSuspended&&"memory".equals(pause)){kill();cpuShares.nextProcess();}
                    if(gpu!=null)gpu.hold("memory".equals(pause)?"gpu_pause_memory":"charging".equals(pause)?"gpu_pause_charging":"gpu_pause_main");
                    cpuRate=cpuAverage=0;if(child==null)cpuRss=0;cpuConnected=false;
                    State.status=pause;State.rate=State.average=0;State.connected=false;State.rssMiB=0;
                }else{
                    if(child!=null&&(cpuThreads.needsRestart(activeThreads)||!launchedMask.equals(thermalMask))){
                        readStats();kill();joinReader();cpuShares.nextProcess();cpuRate=cpuAverage=0;cpuRss=0;cpuConnected=false;
                        State.log("[CPU] Automatic load adjustment: "+State.threads+" -> "+activeThreads+" threads (ceiling "+threads+")");
                    }
                    if(child==null){State.threads=activeThreads;cpuThreads.launched(activeThreads);launch(mode,activeThreads,matrix,pool,wallet,worker);wake.acquire(3600000);}
                    if(cpuSuspended){
                        if(!ProcessControl.resume(child,childPid))throw new IOException("CPU resume failed");
                        cpuSuspended=false;State.computing=true;State.log("[CPU] Resumed retained matrix state");
                    }
                    if(!wake.isHeld())wake.acquire(3600000);
                    if(!child.isAlive()){
                        int code=child.exitValue();joinReader();child=null;State.log("Native core exited: "+code);
                        cpuShares.nextProcess();cpuRate=cpuAverage=0;cpuRss=0;cpuConnected=false;
                        if(gpu!=null)gpu.hold("gpu_pause_main");combineStats();
                        if(benchmark||++retries>3)throw new IOException("Native core stopped (exit "+code+")");
                        State.connected=false;State.rate=0;Thread.sleep(1500L*retries);continue;
                    }
                    readStats();
                    if(gpu!=null){
                        String reason=!gpuCool?gpuThermal.reason():mem.lowMemory||mem.availMem<=768L*1048576L?"gpu_pause_memory":"";
                        gpu.tick(reason,mode,pool,wallet,worker);
                    }
                    State.status=benchmark?"benchmark":cpuConnected?"mining":"connecting";
                }
                State.availableMiB=(int)(mem.availMem/1048576L);combineStats();
                // Remember a measured stable profile for 24 hours, scoped to
                // core, driver, matrix, thread ceiling and temperature limits.
                String load=thermalTier+":"+activeThreads;
                boolean stable=!spreadCpu&&State.autoCpu&&gpu!=null&&gpu.usable()&&State.computing&&State.cpuRate>0&&level<3&&Float.isFinite(State.cpu)&&State.cpu<=cpuLimit-4;
                if(!stable){stableSince=-1;stableLoad="";}
                else if(!load.equals(stableLoad)){stableLoad=load;stableSince=now;}
                else if(now-stableSince>=60000&&!load.equals(savedTune)){
                    prefs().edit().putString("cpuTuneKey",tuneKey).putInt("cpuTuneThreads",activeThreads).putInt("cpuTuneTier",thermalTier).putLong("cpuTuneTime",System.currentTimeMillis()).apply();
                    savedTune=load;State.log("[CPU] Saved stable automatic profile: "+activeThreads+" threads, mask="+thermalMask);
                }
                State.sample();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(101,notification());Thread.sleep(1000);
            }
        }catch(Exception e){if(!stopping){State.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();State.log("ERROR: "+State.error);State.status="failed";}}
        finally{
            kill();closeGpu();joinReader();combineStats();State.cpuRate=State.gpuRate=0;State.gpuStatus="gpu_off";State.active=false;State.connected=false;State.rate=0;State.rssMiB=0;State.elapsed=SystemClock.elapsedRealtime()-State.started;
            if(stopping)State.status="stopped";if(wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
        }
    }
    private void sampleTemperatures(){
        CpuTemperature.request(this);CpuTemperature.Sample s=CpuTemperature.snapshot();
        long now=SystemClock.elapsedRealtime();State.cpu=s.fresh(now)?s.maximum():Float.NaN;State.gpuTemperature=s.gpuFresh(now)?s.gpuMaximum():Float.NaN;
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        State.battery=b!=null&&b.hasExtra(BatteryManager.EXTRA_TEMPERATURE)?b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f:Float.NaN;
    }
    static String verificationKey(String matrix){return CORE+":"+matrix;}
    private void selfTest(int threads,String matrix)throws Exception{
        sampleTemperatures();
        ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mem);
        if(mem.lowMemory||mem.availMem<(MiningConfig.memoryBudgetMiB(matrix)+256L)*1048576L)throw new IOException("Not enough free memory for core self-test");
        if(Build.VERSION.SDK_INT>=29&&((PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus()>=3)throw new IOException("Core self-test requires a cool device");
        if((Float.isFinite(State.battery)&&State.battery>=prefs().getInt("batLimit",43))||(Float.isFinite(State.cpu)&&State.cpu>=prefs().getInt("cpuLimit",92)))throw new IOException("Core self-test requires a cool device");
        wake.acquire(150000);
        if(!runCheck(Arrays.asList("--simd-test","--threads",String.valueOf(threads),"--m","4","--n","4"),30000))return;
        proofPass=false;
        if(!runCheck(Arrays.asList("--mock","--mock-diff","30","--verify","--threads",String.valueOf(threads),"--m",String.valueOf(MiningConfig.rows(matrix)),"--n",String.valueOf(MiningConfig.columns(matrix))),90000))return;
        if(!proofPass)throw new IOException("V3 proof verification did not pass");
        State.verified=true;prefs().edit().putString("verifiedCore",verificationKey(matrix)).apply();State.log("PASS: ARM SIMD parity + V3 proof verified locally; no pool submission");
    }
    private boolean runCheck(List<String> args,int timeout)throws Exception{
        child=process(args);startReader(child);long end=SystemClock.elapsedRealtime()+timeout;
        while(!stopping&&child.isAlive()&&SystemClock.elapsedRealtime()<end){
            sampleTemperatures();
            if((Float.isFinite(State.cpu)&&State.cpu>=prefs().getInt("cpuLimit",92))||(Float.isFinite(State.battery)&&State.battery>=prefs().getInt("batLimit",43))||
                (Build.VERSION.SDK_INT>=29&&((PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus()>=3))throw new IOException("Self-test paused by temperature limit");
            Thread.sleep(250);
        }
        if(stopping)return false;
        if(child.isAlive())throw new IOException("Self-test timeout");
        int result=child.exitValue();joinReader();child=null;if(result!=0)throw new IOException("Self-test failed: "+result);return true;
    }
    private void launch(String mode,int threads,String matrix,String pool,String wallet,String worker)throws Exception{
        try(ServerSocket s=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=s.getLocalPort();}
        ArrayList<String> a=new ArrayList<>(Arrays.asList("--algo","pearl","--backend","cpu","--threads",String.valueOf(threads),"--m",String.valueOf(MiningConfig.rows(matrix)),"--n",String.valueOf(MiningConfig.columns(matrix)),"--verify","--api-port",String.valueOf(port),"--api-bind","127.0.0.1","--agent","GozeroPearl/1.0"));
        if("bench".equals(mode))Collections.addAll(a,"--mock","--mock-diff","90");
        else Collections.addAll(a,"--pool",pool,"--wallet",wallet,"--worker",worker);
        child=process(a);startReader(child);
    }
    private Process process(List<String> args)throws IOException{
        ArrayList<String> command=new ArrayList<>();command.add(getApplicationInfo().nativeLibraryDir+"/libpearlminer.so");command.addAll(args);
        launchedMask=thermalMask;
        ProcessBuilder p=new ProcessBuilder(ProcessControl.command(ThermalCpuSet.apply(command,thermalMask))).directory(getFilesDir()).redirectErrorStream(true);
        p.environment().put("OMP_WAIT_POLICY","PASSIVE");p.environment().put("KMP_BLOCKTIME","0");
        p.environment().put("RAYON_NUM_THREADS",spreadCpu?"1":"2");
        if(spreadCpu){p.environment().put("CP_CPU_AFFINITY","1");p.environment().remove("OMP_PROC_BIND");p.environment().remove("OMP_PLACES");p.environment().put("OMP_DYNAMIC","false");p.environment().put("CP_CPU_DUTY_PERCENT","40");p.environment().put("CP_CPU_DUTY_MASK",ThermalCpuSet.pacingMask());p.environment().put("CP_CPU_DUTY_SECONDARY_MASK",ThermalCpuSet.secondaryPacingMask());p.environment().put("CP_CPU_DUTY_FILE",new File(getFilesDir(),"cpu-duty").getAbsolutePath());}
        childPid=0;cpuSuspended=false;Process result=p.start();State.computing=true;return result;
    }
    private void writeDuty(int duty)throws IOException{
        if(duty==writtenDuty)return;
        try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"cpu-duty"))){out.write((duty+"\n").getBytes(StandardCharsets.US_ASCII));}
        writtenDuty=duty;State.log("[CPU] Adaptive work pacing: "+duty+"% (temperature limits unchanged)");
    }
    private void startReader(Process p){
        reader=new Thread(()->{
            try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null){
                int pid=ProcessControl.pid(line);if(pid>0){if(child==p)childPid=pid;continue;}
                State.log(line);
                  if(line.contains("SIMD ISA:"))State.simd=line.substring(line.indexOf("SIMD ISA:")+9).trim();
                  int actualThreads=CpuWorkerThreads.actual(line);
                  if(child==p&&actualThreads>0)State.threads=actualThreads;
                if(line.contains("[mock] PASS:"))proofPass=true;
            }}catch(IOException e){if(!stopping)State.log("Log stream closed");}
        },"pearl-output");reader.start();
    }
    private void readStats(){
        try{
            NativeStats s=NativeStats.read(port);cpuRate=s.rate;cpuAverage=s.average;cpuConnected=s.connected;cpuRss=s.rssMiB;
            cpuShares.update(s.accepted,s.rejected);
        }catch(Exception e){cpuRate=cpuAverage=0;cpuConnected=false;}
    }
    private void combineStats(){
        GpuWorker g=gpu;State.cpuRate=cpuRate;State.gpuRate=g==null?0:g.rate;
        State.rate=cpuRate+State.gpuRate;State.average=cpuAverage+(g==null?0:g.average);
        State.rssMiB=cpuRss+(g==null?0:g.rssMiB);State.connected=cpuConnected;
        State.accepted=cpuShares.accepted()+(g==null?0:g.shares.accepted());
        State.rejected=cpuShares.rejected()+(g==null?0:g.shares.rejected());
        State.gpuStatus=g==null?"gpu_off":g.status;
    }
    private void closeGpu(){GpuWorker g=gpu;if(g!=null)g.close();}
    private void joinReader(){try{if(reader!=null)reader.join(1500);}catch(InterruptedException e){Thread.currentThread().interrupt();}reader=null;}
    private synchronized void kill(){State.computing=false;Process p=child;if(p!=null){if(cpuSuspended){ProcessControl.kill(p,childPid);p.destroyForcibly();}else p.destroy();try{if(!p.waitFor(350,TimeUnit.MILLISECONDS)){ProcessControl.kill(p,childPid);p.destroyForcibly();p.waitFor(350,TimeUnit.MILLISECONDS);}}catch(InterruptedException e){p.destroyForcibly();Thread.currentThread().interrupt();}child=null;}childPid=0;cpuSuspended=false;}
    @Override public void onDestroy(){stopping=true;kill();closeGpu();executor.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();super.onDestroy();}
}
