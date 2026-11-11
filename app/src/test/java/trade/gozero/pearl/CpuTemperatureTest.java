package trade.gozero.pearl;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CpuTemperatureTest {
    @Test public void onlyCpuAndGpuZonesAreSelected(){
        for(String name:new String[]{"cpu-2-2-1","cpuss-0","CPU7","cpu-1-10"})assertEquals(0,CpuTemperature.kind(name));
        assertEquals(1,CpuTemperature.kind("gpuss-7"));assertEquals(1,CpuTemperature.kind("GPU0"));
        for(String name:new String[]{"battery","AP","skin","nsp0","pm8550_tz","cpu-therm-virtual","cpu-gpu"})assertEquals(-1,CpuTemperature.kind(name));
    }
    @Test public void sysfsUnitsAndDisconnectedSensorSentinels(){
        assertEquals(92.0f,CpuTemperature.milliCelsius("92000\n"),0.001f);
        assertEquals(32.883f,CpuTemperature.milliCelsius("32883"),0.001f);
        for(String raw:new String[]{"-273000","-40960","0","NaN","Infinity","150001","error"})assertTrue(Float.isNaN(CpuTemperature.milliCelsius(raw)));
    }
    @Test public void halCachedAndOtherTypesCannotBecomeCpuTemperature(){
        String raw="Cached temperatures:\nTemperature{mValue=98.0, mType=0, mName=CPU0, mStatus=0}\nCurrent temperatures from HAL:\n"
            +"Temperature{mValue=55.0, mType=0, mName=AP, mStatus=0}\nTemperature{mValue=61.0, mType=1, mName=GPU0, mStatus=0}\n"
            +"Temperature{mValue=99.0, mType=3, mName=SKIN, mStatus=0}\nCurrent cooling devices from HAL:\nTemperature{mValue=100.0, mType=0, mName=CPU9, mStatus=0}";
        assertEquals(Collections.singletonMap("AP",55f),CpuTemperature.parseCurrent(raw));
        assertEquals(Collections.singletonMap("GPU0",61f),CpuTemperature.parseHal(raw,1));
        assertTrue(CpuTemperature.parseCurrent("Cached temperatures:\nTemperature{mValue=98.0, mType=0, mName=CPU0, mStatus=0}").isEmpty());
    }
    @Test public void maximumIsNotAverageAndStaleDataExpires(){
        Map<String,Float> cpu=new HashMap<>();cpu.put("cpu-0-0",52f);cpu.put("cpu-2-0",91f);
        CpuTemperature.Sample s=new CpuTemperature.Sample(cpu,Collections.singletonMap("gpuss-0",66f),1000,"sensor_direct","sensor_direct");
        cpu.put("cpu-2-0",10f);assertEquals(91f,s.maximum(),0);assertEquals(66f,s.gpuMaximum(),0);
        assertTrue(s.fresh(5000));assertFalse(s.fresh(5001));assertFalse(s.fresh(999));assertEquals("sensor_stale",s.cpuLabel(5001));
    }
    @Test public void unavailableCpuNeverUsesGpuAsReplacement(){
        CpuTemperature.Sample s=new CpuTemperature.Sample(Collections.emptyMap(),Collections.singletonMap("gpuss-0",66f),1000,"sensor_unavailable","sensor_direct");
        assertFalse(s.fresh(1000));assertTrue(Float.isNaN(s.maximum()));assertTrue(s.gpuFresh(1000));
    }
}
