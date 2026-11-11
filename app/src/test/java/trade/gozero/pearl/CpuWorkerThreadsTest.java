package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;

public class CpuWorkerThreadsTest {
    @Test public void nativeThreadCapDoesNotTriggerRestartLoop(){
        CpuWorkerThreads state=new CpuWorkerThreads();state.launched(7);
        assertEquals(5,CpuWorkerThreads.actual("[cpu] affinity: 5 cores, 5 OpenMP threads, pinned (cores first, then SMT siblings)"));
        assertFalse(state.needsRestart(7));assertTrue(state.needsRestart(4));
        state.launched(4);assertFalse(state.needsRestart(4));assertTrue(state.needsRestart(7));
    }
    @Test public void onlyAcceptsCpuAffinityThreadReport(){
        assertEquals(2,CpuWorkerThreads.actual("[cpu] affinity: 2 physical + 2 SMT, 4 logical CPUs, 2 OpenMP threads, pinned"));
        for(String s:new String[]{"[cpu] affinity: unavailable","[gpu] affinity: 4 cores, 4 OpenMP threads","pool says 4 OpenMP threads","[cpu] affinity: 4 cores, 0 OpenMP threads","[cpu] affinity: 4 cores, 99 OpenMP threads"})assertEquals(0,CpuWorkerThreads.actual(s));
    }
}
