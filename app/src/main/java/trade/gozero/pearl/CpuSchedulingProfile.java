package trade.gozero.pearl;

import java.util.Locale;

/** SM8650 profile: independent workers with pacing on the hottest capacity tier. */
final class CpuSchedulingProfile {
    static boolean spread(String soc,boolean automatic){
        String name=soc==null?"":soc.trim().toUpperCase(Locale.ROOT);
        return automatic&&(name.equals("SM8650")||name.startsWith("SM8650-"));
    }
    static int spreadThreads(int ceiling){return Math.max(1,Math.min(7,ceiling));}
}
