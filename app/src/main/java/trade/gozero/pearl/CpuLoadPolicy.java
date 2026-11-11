package trade.gozero.pearl;

/** Optional load shedding, within the user's thread ceiling; never raises thermal limits. */
final class CpuLoadPolicy {
    private int current;
    private int thermalCeiling=8;
    private long changed=-45000,coolSince=-1;
    private long severeChanged=-45000;
    private boolean wasSevere;
    void restore(int threads){current=thermalCeiling=Math.max(1,Math.min(8,threads));}
    int threads(int ceiling,float cpu,float battery,int system,int cpuLimit,int batteryLimit,long now){
        ceiling=Math.max(1,Math.min(8,ceiling));
        if(current==0)current=ceiling;
        current=Math.min(current,ceiling);
        boolean severe=system>=3||(Float.isFinite(cpu)&&cpu>=cpuLimit)||(Float.isFinite(battery)&&battery>=batteryLimit);
        // A new thermal-stop episode lowers the next running load. Do not
        // repeatedly decrement while the worker is already paused.
        long severeGap=Float.isFinite(cpu)&&cpu>=cpuLimit?3000:45000;
        if(severe&&!wasSevere&&now-severeChanged>=severeGap){
            current=Math.max(1,Math.min(3,current-1));
            thermalCeiling=Math.min(thermalCeiling,current);
            changed=severeChanged=now;coolSince=-1;
        }
        wasSevere=severe;
        boolean hot=system>=3||(Float.isFinite(battery)&&battery>=Math.min(41,batteryLimit-2))||(Float.isFinite(cpu)&&cpu>=cpuLimit-2);
        // Android may report MODERATE before the battery or readable CPU
        // sensor heats up. Treat that signal independently, like the GPU gate.
        boolean warm=system>=2||(Float.isFinite(cpu)&&cpu>=cpuLimit-5);
        // Once a load actually exceeded a limit, do not retry that failed load
        // every minute in the same session. A new user session re-evaluates it.
        int target=Math.min(thermalCeiling,hot?Math.min(4,ceiling):warm?Math.min(5,ceiling):ceiling);
        if(target<current){
            coolSince=-1;
            if(system>=3||now-changed>=45000){current=target;changed=now;}
        }else if(target>current){
            boolean cool=system<=1&&Float.isFinite(battery)&&battery<=Math.min(39,batteryLimit-4)&&(!Float.isFinite(cpu)||cpu<=cpuLimit-8);
            if(cool){if(coolSince<0)coolSince=now;if(now-coolSince>=60000){current=Math.min(target,current+1);changed=now;coolSince=-1;}}else coolSince=-1;
        }else coolSince=-1;
        return current;
    }
}
