package trade.gozero.pearl;

import android.system.Os;
import android.system.OsConstants;
import java.util.*;

/** PID handshake from our own child; no /proc search, reflection, or shell interpolation. */
final class ProcessControl {
    private static final String PREFIX="[gozero-child-pid] ";
    static List<String> command(List<String> args){
        ArrayList<String> out=new ArrayList<>(Arrays.asList("/system/bin/sh","-c","echo '[gozero-child-pid] '$$; exec \"$@\"","gozero-child"));
        out.addAll(args);return out;
    }
    static int pid(String line){
        if(!line.startsWith(PREFIX))return 0;
        try{int value=Integer.parseInt(line.substring(PREFIX.length()).trim());return value>0?value:0;}catch(NumberFormatException e){return 0;}
    }
    static boolean suspend(Process p,int pid){return signal(p,pid,OsConstants.SIGSTOP);}
    static boolean resume(Process p,int pid){return signal(p,pid,OsConstants.SIGCONT);}
    static boolean kill(Process p,int pid){return signal(p,pid,OsConstants.SIGKILL);}
    private static boolean signal(Process p,int pid,int signal){
        if(p==null||pid<=0||!p.isAlive())return false;
        try{Os.kill(pid,signal);return true;}catch(Exception e){return false;}
    }
}
