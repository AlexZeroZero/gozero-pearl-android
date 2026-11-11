package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;
public class CpuSchedulingProfileTest {
    @Test public void onlyAutomaticTestedSocUsesDistributedWorkers(){
        assertTrue(CpuSchedulingProfile.spread("SM8650",true));
        assertTrue(CpuSchedulingProfile.spread(" sm8650-ac ",true));
        assertFalse(CpuSchedulingProfile.spread("SM8650",false));
        for(String soc:new String[]{null,"","SM8550","SM86500","Exynos 2400"})assertFalse(CpuSchedulingProfile.spread(soc,true));
    }
    @Test public void requestedCeilingIsNeverExceeded(){
        for(int n=1;n<=8;n++)assertEquals(Math.min(n,7),CpuSchedulingProfile.spreadThreads(n));
        assertEquals(1,CpuSchedulingProfile.spreadThreads(0));
    }
    @Test public void onlyFastTierIsPacedAndPrimeRemainsExcluded(){
        assertEquals("1c",ThermalCpuSet.pacingMask(new int[]{369,369,897,897,897,842,842,1024}));
        assertEquals("",ThermalCpuSet.pacingMask(new int[]{0,0,0,0}));
        assertEquals("",ThermalCpuSet.pacingMask(new int[]{100,100,100,100}));
    }
}
