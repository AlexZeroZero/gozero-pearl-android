package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;
public class ThermalPolicyTest {
    @Test public void ninetyTwoLimitResumesAtNinetyWithoutTimeDelay(){
        ThermalPolicy p=new ThermalPolicy();assertFalse(p.pause(91.9f,38,0,92,43,0));
        assertTrue(p.pause(92,38,0,92,43,1000));assertTrue(p.pause(90.1f,38,0,92,43,1100));
        assertFalse(p.pause(90,38,0,92,43,1200));assertFalse(p.pause(91,38,0,92,43,1300));
        assertTrue(p.pause(92,38,0,92,43,1400));
    }
    @Test public void coolingCpuDoesNotOverrideBatteryOrAndroid(){
        ThermalPolicy p=new ThermalPolicy();assertTrue(p.pause(92,43,3,92,43,0));
        assertTrue(p.pause(89,41,3,92,43,1000));assertTrue(p.pause(89,42,0,92,43,2000));assertFalse(p.pause(89,41,0,92,43,3000));
    }
    @Test public void batteryHasHysteresis(){ThermalPolicy p=new ThermalPolicy();assertTrue(p.pause(60,43,0,85,43,0));assertTrue(p.pause(60,42,0,85,43,4000));assertFalse(p.pause(60,41,0,85,43,5000));assertFalse(p.pause(60,41,0,85,43,8000));}
    @Test public void cpuAndSystemMustBothCool(){ThermalPolicy p=new ThermalPolicy();assertTrue(p.pause(90,30,3,85,43,0));assertTrue(p.pause(79,30,3,85,43,4000));assertFalse(p.pause(79,30,2,85,43,5000));assertFalse(p.pause(79,30,2,85,43,8000));}
    @Test public void absentCpuSensorDoesNotPreventStarting(){assertFalse(new ThermalPolicy().pause(Float.NaN,30,0,85,43,0));}
    @Test public void unknownBatteryDoesNotResumeCooling(){ThermalPolicy p=new ThermalPolicy();assertTrue(p.pause(60,43,0,85,43,0));assertTrue(p.pause(60,Float.NaN,0,85,43,5000));}
    @Test public void missingCpuAfterCpuOverheatDoesNotResume(){ThermalPolicy p=new ThermalPolicy();assertTrue(p.pause(90,30,0,85,43,0));assertTrue(p.pause(Float.NaN,30,0,85,43,4000));assertTrue(p.pause(Float.NaN,30,0,85,43,8000));}
}
