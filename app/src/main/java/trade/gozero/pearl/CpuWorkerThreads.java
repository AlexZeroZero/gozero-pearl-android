package trade.gozero.pearl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keep the requested ceiling separate from native cpuset-limited execution. */
final class CpuWorkerThreads {
    private static final Pattern AFFINITY=Pattern.compile("^\\[cpu] affinity: .*?\\b([1-8]) OpenMP threads(?:\\b.*)?$");
    private int requested;
    void launched(int threads){requested=threads;}
    boolean needsRestart(int target){return target!=requested;}
    static int actual(String line){
        Matcher match=AFFINITY.matcher(line);
        return match.matches()?Integer.parseInt(match.group(1)):0;
    }
}
