package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResourcePolicyTest {
    @Test public void repeatedDieOverheatReducesLoadPromptlyWithoutPerSampleDecrement(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(3,p.threads(7,92,36,0,92,43,0));
        assertEquals(3,p.threads(7,92,36,0,92,43,1000));
        assertEquals(3,p.threads(7,70,36,0,92,43,2000));
        assertEquals(2,p.threads(7,92,36,0,92,43,4000));
        assertEquals(2,p.threads(7,70,36,0,92,43,5000));
        assertEquals(1,p.threads(7,92,36,0,92,43,8000));
        p.restore(2);assertEquals(1,p.threads(1,60,36,0,92,43,9000));
    }
    @Test public void failedLoadIsNotRetriedEveryMinuteDuringSameSession(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(7,p.threads(7,60,36,0,92,43,0));
        assertEquals(3,p.threads(7,92,36,0,92,43,1000));
        assertEquals(3,p.threads(7,60,36,0,92,43,2000));
        assertEquals(3,p.threads(7,60,36,0,92,43,200000));
        assertEquals(2,p.threads(7,92,36,0,92,43,201000));
        assertEquals(2,p.threads(7,60,36,0,92,43,202000));
        assertEquals(2,p.threads(7,60,36,0,92,43,400000));
    }
    @Test public void cpuShedsLoadBeforeThermalStopAndDoesNotOscillate(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(6,p.threads(6,Float.NaN,38,1,90,45,0));
        assertEquals(5,p.threads(6,Float.NaN,40,2,90,45,1000));
        assertEquals(5,p.threads(6,Float.NaN,41,2,90,45,2000));
        assertEquals(4,p.threads(6,Float.NaN,41,2,90,45,46000));
        assertEquals(4,p.threads(6,Float.NaN,38,1,90,45,47000));
        assertEquals(4,p.threads(6,Float.NaN,38,1,90,45,106999));assertEquals(5,p.threads(6,Float.NaN,38,1,90,45,107000));
        assertEquals(5,p.threads(6,Float.NaN,38,1,90,45,107001));assertEquals(6,p.threads(6,Float.NaN,38,1,90,45,167001));
    }
    @Test public void cpuRespectsSmallCeilingsAndTemperatureLimits(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(1,p.threads(2,90,44,3,85,43,0));
        p=new CpuLoadPolicy();assertEquals(3,p.threads(8,Float.NaN,Float.NaN,3,85,43,0));
        assertEquals(3,p.threads(8,Float.NaN,Float.NaN,0,85,43,90000));
    }
    @Test public void androidModerateIsActionableWithCoolOrUnknownSensors(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(7,p.threads(7,50,36,0,85,43,0));
        assertEquals(5,p.threads(7,52,36,2,85,43,1000));
        p=new CpuLoadPolicy();assertEquals(5,p.threads(7,Float.NaN,Float.NaN,2,85,43,0));
    }
    @Test public void repeatedStopEpisodesCanEscapeTheOldFourThreadFloor(){
        CpuLoadPolicy p=new CpuLoadPolicy();assertEquals(7,p.threads(7,50,36,0,85,43,0));
        assertEquals(5,p.threads(7,50,36,2,85,43,1000));
        assertEquals(3,p.threads(7,50,37,3,85,43,10000));
        assertEquals(3,p.threads(7,50,37,3,85,43,20000));
        assertEquals(3,p.threads(7,50,37,2,85,43,40000));
        assertEquals(2,p.threads(7,50,38,3,85,43,56000));
        assertEquals(2,p.threads(7,50,38,3,85,43,60000));
        assertEquals(1,p.threads(1,50,38,2,85,43,65000));
    }
    @Test public void gpuResumesOnFirstCoolSampleAndExplainsSystemPause(){
        GpuPolicy p=new GpuPolicy();assertTrue(p.allow(70,60,38,0,92,43,0));
        assertFalse(p.allow(70,60,38,2,92,43,1000));assertEquals("gpu_pause_system",p.reason());
        assertTrue(p.allow(70,60,38,1,92,43,2000));
        assertFalse(p.allow(89,60,38,0,92,43,3000));assertEquals("gpu_pause_cpu",p.reason());
        assertFalse(p.allow(87,60,38,0,92,43,4000));assertTrue(p.allow(86,60,38,0,92,43,5000));
    }
    @Test public void gpuOwnSensorHasHysteresisAndCannotResumeOnLostReading(){
        GpuPolicy p=new GpuPolicy();assertFalse(p.allow(70,90,38,0,92,43,0));assertEquals("gpu_pause_heat",p.reason());
        assertFalse(p.allow(70,89,38,0,92,43,1000));
        assertFalse(p.allow(70,Float.NaN,38,0,92,43,2000));assertEquals("gpu_pause_sensor",p.reason());
        assertTrue(p.allow(70,88,38,0,92,43,3000));
    }
    @Test public void gpuBatteryLimitAndUnknownReadingFailClosed(){
        GpuPolicy p=new GpuPolicy();assertFalse(p.allow(Float.NaN,Float.NaN,Float.NaN,0,92,43,0));
        assertFalse(p.allow(Float.NaN,Float.NaN,37,0,92,38,20000));assertEquals("gpu_pause_battery",p.reason());
        assertTrue(p.allow(Float.NaN,Float.NaN,36,0,92,38,21000));
        assertFalse(p.allow(Float.NaN,Float.NaN,41,0,92,45,22000));
    }
    @Test public void sharesRemainSeparateAcrossPausesAndFailures(){
        ShareTotals cpu=new ShareTotals(),gpu=new ShareTotals();cpu.update(5,1);gpu.update(2,0);
        cpu.nextProcess();cpu.update(3,0);assertEquals(10,cpu.accepted()+gpu.accepted());
        gpu.nextProcess();gpu.nextProcess();gpu.update(1,1);assertEquals(11,cpu.accepted()+gpu.accepted());assertEquals(2,cpu.rejected()+gpu.rejected());
        gpu.update(0,0);assertEquals(3,gpu.accepted());cpu.reset();gpu.reset();assertEquals(0,cpu.accepted()+gpu.accepted());
    }
}
