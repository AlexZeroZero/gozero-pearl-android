package trade.gozero.pearl;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ProcessControlTest {
    @Test public void pidHandshakeRejectsUnrelatedAndMalformedLines(){
        assertEquals(123,ProcessControl.pid("[gozero-child-pid] 123"));
        for(String s:new String[]{"pid 123","[gozero-child-pid] -1","[gozero-child-pid] 0","[gozero-child-pid] 12;kill","[gozero-child-pid] 999999999999"})assertEquals(0,ProcessControl.pid(s));
    }
    @Test public void commandArgumentsAreNeverInterpolatedIntoShellCode(){
        List<String> args=Arrays.asList("/data/app/libpearlminer.so","--worker","literal;$(echo bad)");List<String> cmd=ProcessControl.command(args);
        assertEquals(args,cmd.subList(4,cmd.size()));assertEquals("gozero-child",cmd.get(3));assertFalse(cmd.get(2).contains("literal"));
    }
    @Test public void uniquePrimeCanBeAvoidedWithoutRemovingPerformanceCluster(){
        assertEquals("7f",ThermalCpuSet.mask(new int[]{369,369,897,897,897,842,842,1024}));
        assertEquals("",ThermalCpuSet.mask(new int[]{1024,1024}));assertEquals("",ThermalCpuSet.mask(new int[]{1024}));assertEquals("",ThermalCpuSet.mask(new int[]{}));
        assertEquals("3",ThermalCpuSet.mask(new int[]{300,700,1024,0}));
    }
    @Test public void repeatedCpuOverheatRetainsAnEfficientPerformanceTier(){
        assertEquals("63",ThermalCpuSet.coolerMask(new int[]{369,369,897,897,897,842,842,1024},2));
        assertEquals("3f",ThermalCpuSet.coolerMask(new int[]{300,300,300,700,700,700,800,1024},2));
        assertEquals("7f",ThermalCpuSet.coolerMask(new int[]{300,300,300,700,700,700,700,1024},2));
        assertEquals("",ThermalCpuSet.coolerMask(new int[]{1024,1024},2));
        assertEquals("",ThermalCpuSet.coolerMask(new int[]{},2));
        assertEquals("3",ThermalCpuSet.coolerMask(new int[]{369,369,897,897,897,842,842,1024},3));
        assertEquals("",ThermalCpuSet.coolerMask(new int[]{1024,1024},3));
    }
}
