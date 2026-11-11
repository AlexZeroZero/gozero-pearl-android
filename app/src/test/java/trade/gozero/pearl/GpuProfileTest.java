package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuProfileTest {
    @Test public void optimizedLayoutIsLimitedToTestedSocFamily(){
        assertEquals("4x8/64x64",GpuProfile.forSoc("SM8550").tile);
        assertEquals("4x8/64x64",GpuProfile.forSoc(" sm8550-ac ").tile);
        assertTrue(GpuProfile.forSoc("SM8550").vulkan);
        assertFalse(GpuProfile.forSoc("SM8650").vulkan);
        assertEquals("4x8/64x256",GpuProfile.forSoc("SM8650").tile);
        assertEquals("4x8/64x256",GpuProfile.forSoc("SM8650-AC").tile);
        for(String soc:new String[]{null,"","unknown","SM85500","Exynos 2400"})
            assertEquals("8x8/64x64",GpuProfile.forSoc(soc).tile);
    }
    @Test public void proofCacheCannotSurviveLayoutCoreOrSystemChanges(){
        GpuProfile fast=GpuProfile.forSoc("SM8550"),fallback=GpuProfile.forSoc("");
        String key=fast.verificationKey("core-a","system-a");
        assertNotEquals("core-a:system-a",key); // old 0.4.0 proof cache
        assertNotEquals(fallback.verificationKey("core-a","system-a"),key);
        assertNotEquals(fast.verificationKey("core-b","system-a"),key);
        assertNotEquals(fast.verificationKey("core-a","system-b"),key);
        assertNotEquals(GpuProfile.forSoc("SM8650").verificationKey("core-a","system-a"),key);
        GpuProfile fallbackGpu=GpuProfile.openClForSoc("SM8550");
        assertFalse(fallbackGpu.vulkan);assertEquals("4x8/128x128",fallbackGpu.tile);
        assertNotEquals(fallbackGpu.verificationKey("core-a","system-a"),key);
    }
}
