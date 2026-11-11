package trade.gozero.pearl;

/** Keep each pause cause visible; resume on the first cool sample, without a timer. */
final class GpuPolicy {
    static final int GPU_LIMIT=90;
    private boolean cooling,cpuHot,gpuHot;
    private String reason="";
    String reason(){return reason;}
    boolean allow(float cpu,float gpu,float battery,int system,int cpuLimit,int batteryLimit,long now){
        int batteryStop=Math.min(41,batteryLimit-1);
        if(Float.isFinite(gpu)&&gpu>=GPU_LIMIT)gpuHot=true;
        if(Float.isFinite(cpu)&&cpu>=cpuLimit-3)cpuHot=true;
        String stop=system>=2?"gpu_pause_system":!Float.isFinite(battery)?"gpu_pause_sensor":battery>=batteryStop?"gpu_pause_battery":Float.isFinite(gpu)&&gpu>=GPU_LIMIT?"gpu_pause_heat":Float.isFinite(cpu)&&cpu>=cpuLimit-3?"gpu_pause_cpu":"";
        if(!stop.isEmpty()){cooling=true;reason=stop;return false;}
        if(!cooling){reason="";return true;}
        if((cpuHot&&!Float.isFinite(cpu))||(gpuHot&&!Float.isFinite(gpu))){reason="gpu_pause_sensor";return false;}
        if(battery>batteryStop-1){reason="gpu_pause_battery";return false;}
        if(Float.isFinite(gpu)&&gpu>GPU_LIMIT-2){reason="gpu_pause_heat";return false;}
        if(Float.isFinite(cpu)&&cpu>cpuLimit-6){reason="gpu_pause_cpu";return false;}
        cooling=cpuHot=gpuHot=false;reason="";return true;
    }
}
