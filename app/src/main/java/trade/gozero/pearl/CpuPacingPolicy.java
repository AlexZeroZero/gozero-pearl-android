package trade.gozero.pearl;

/** Reversible power pacing for the distributed SM8650 workers. */
final class CpuPacingPolicy {
    private int duty=40;
    private long changed=-10000,coolSince=-1;
    private boolean wasHot;
    int update(float cpu,int system,int limit,long now){
        boolean hot=system>=3||(Float.isFinite(cpu)&&cpu>=limit-1);
        if(hot&&!wasHot){duty=Math.max(20,duty-20);changed=now;}
        wasHot=hot;
        if(hot){coolSince=-1;return duty;}
        if(Float.isFinite(cpu)&&cpu>=limit-4){
            coolSince=-1;
            if(now-changed>=2000){duty=Math.max(20,duty-5);changed=now;}
        }else if(Float.isFinite(cpu)&&cpu<=limit-8&&system<3){
            if(coolSince<0)coolSince=now;
            if(now-coolSince>=5000&&now-changed>=5000){duty=Math.min(100,duty+5);changed=now;coolSince=now;}
        }else coolSince=-1;
        return duty;
    }
}
