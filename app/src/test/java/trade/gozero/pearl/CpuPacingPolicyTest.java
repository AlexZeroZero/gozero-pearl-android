package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;
public class CpuPacingPolicyTest {
    @Test public void oneHotEpisodeDoesNotRepeatedlyShedLoad(){
        CpuPacingPolicy p=new CpuPacingPolicy();
        assertEquals(20,p.update(93,2,92,0));
        assertEquals(20,p.update(94,2,92,1000));
        assertEquals(20,p.update(93,2,92,10000));
    }
    @Test public void repeatedDistinctHeatEventsCanReduceOnlyThePacedTierToTwenty(){
        CpuPacingPolicy p=new CpuPacingPolicy();
        for(int t=0;t<10000;t+=2000){p.update(93,2,92,t);p.update(80,2,92,t+1000);}
        assertEquals(20,p.update(93,2,92,10000));
    }
    @Test public void cooledWorkerRecoversInsteadOfCachingFallback(){
        CpuPacingPolicy p=new CpuPacingPolicy();p.update(93,2,92,0);
        for(int t=1000;t<=101000;t+=1000)p.update(80,2,92,t);
        assertEquals(100,p.update(80,2,92,102000));
    }
    @Test public void missingSensorDoesNotIncreasePowerAndSystemHeatWins(){
        CpuPacingPolicy p=new CpuPacingPolicy();
        assertEquals(40,p.update(Float.NaN,0,92,0));
        assertEquals(40,p.update(Float.NaN,0,92,90000));
        assertEquals(20,p.update(60,3,92,91000));
    }
    @Test public void nearLimitReducesBeforeThermalStopAndHonorsCustomLimit(){
        CpuPacingPolicy p=new CpuPacingPolicy();
        assertEquals(35,p.update(81,0,85,0));
        assertEquals(35,p.update(81,0,85,1000));
        assertEquals(30,p.update(81,0,85,2000));
        assertEquals(20,p.update(85,0,85,3000));
    }
}
