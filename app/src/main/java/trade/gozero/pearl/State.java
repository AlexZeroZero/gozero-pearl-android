package trade.gozero.pearl;

import java.util.*;

final class State {
    static volatile boolean active,computing,connected,verified,autoCpu;
    static volatile String status="ready",mode="mine",simd="ARM64",error="";
    static volatile double rate,average,lastBenchmark,cpuRate,gpuRate;
    static volatile String matrix="8x8",gpuStatus="gpu_off";
    static volatile int threads,threadLimit,rssMiB,accepted,rejected,availableMiB;
    static volatile long started,elapsed;
    static volatile float battery=Float.NaN,cpu=Float.NaN,gpuTemperature=Float.NaN;
    private static final ArrayDeque<String> logs=new ArrayDeque<>();
    private static final ArrayDeque<Float> history=new ArrayDeque<>();
    static synchronized void log(String s){
        s=s.replaceAll("prl1[0-9a-zA-Z]{20,}","prl1…");
        if(s.length()>1400)s=s.substring(0,1400)+"…";
        if(!s.contains("attempt timing:")&&!s.contains("progress:")&&!s.contains("attempts="))android.util.Log.i("GozeroPearl",s);
        logs.addLast(new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date())+"  "+s);
        while(logs.size()>220)logs.removeFirst();
    }
    static synchronized String logText(boolean errors){StringBuilder b=new StringBuilder();for(String s:logs){String l=s.toLowerCase(Locale.ROOT);if(!errors||l.contains("fail")||l.contains("error")||l.contains("reject")||l.contains("warn"))b.append(s).append('\n');}return b.toString();}
    static synchronized void clearLog(){logs.clear();}
    static synchronized void sample(){history.addLast((float)(rate/1e9));while(history.size()>60)history.removeFirst();}
    static synchronized float[] chart(){float[] a=new float[history.size()];int n=0;for(float f:history)a[n++]=f;return a;}
    static synchronized void reset(String m){mode=m;status="preparing";active=true;rate=average=cpuRate=gpuRate=0;gpuStatus="gpu_off";accepted=rejected=rssMiB=0;elapsed=0;connected=false;error="";history.clear();}
}
