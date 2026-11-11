package trade.gozero.pearl;

/** Independent CPU, battery and Android thermal hysteresis. */
final class ThermalPolicy {
    private boolean cooling,cpuCooling;
    boolean pause(float cpu,float battery,int system,int cpuLimit,int batteryLimit,long now){
        if(Float.isFinite(cpu)&&cpu>=cpuLimit)cpuCooling=true;
        if((Float.isFinite(cpu)&&cpu>=cpuLimit)||(Float.isFinite(battery)&&battery>=batteryLimit)||system>=3)cooling=true;
        if(cooling){
            boolean safe=(!cpuCooling||Float.isFinite(cpu))&&(!Float.isFinite(cpu)||cpu<=cpuLimit-2)&&Float.isFinite(battery)&&battery<=batteryLimit-2&&system<3;
            if(safe){cooling=false;cpuCooling=false;}
        }
        return cooling;
    }
}
