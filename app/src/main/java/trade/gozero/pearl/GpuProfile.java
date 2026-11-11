package trade.gozero.pearl;

import java.util.Locale;

/** Device-tested launch layouts; other SoCs retain the conservative layout. */
final class GpuProfile {
    final String tile;
    final int batch;
    final boolean vulkan;
    private GpuProfile(String tile,int batch,boolean vulkan){this.tile=tile;this.batch=batch;this.vulkan=vulkan;}
    static GpuProfile forSoc(String soc){
        String name=soc==null?"":soc.trim().toUpperCase(Locale.ROOT);
        if(name.equals("SM8550")||name.startsWith("SM8550-"))return new GpuProfile("4x8/64x64",256,true);
        return openClForSoc(soc);
    }
    static GpuProfile openClForSoc(String soc){
        String name=soc==null?"":soc.trim().toUpperCase(Locale.ROOT);
        // SM8550 / Adreno 740: smaller register tiles reduce live accumulators.
        // 128x128 groups improve throughput; batch 256 bounds launch latency.
        // SM8650 / Adreno 750: rectangular cache blocking, same 512 work-items.
        // SM8550 regresses with this geometry and keeps the square macro.
        if(name.equals("SM8650")||name.startsWith("SM8650-"))return new GpuProfile("4x8/64x256",256,false);
        if(name.equals("SM8550")||name.startsWith("SM8550-"))return new GpuProfile("4x8/128x128",256,false);
        return new GpuProfile("8x8/64x64",256,false);
    }
    String verificationKey(String core,String system){
        // A changed layout must pass the proof gate even if binaries are unchanged.
        return core+":"+system+":4x4:"+tile+":"+batch+(vulkan?":vulkan-packed-s8-v1":":broadcast-float-v1");
    }
}
