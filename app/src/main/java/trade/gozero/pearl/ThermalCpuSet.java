package trade.gozero.pearl;
import java.io.*;
import java.util.*;

/** On a thermal restart, avoid a unique highest-capacity (prime) CPU. */
final class ThermalCpuSet {
    static String mask(int[] capacities){
        int highest=0,count=0,index=-1,present=0;long mask=0;
        for(int i=0;i<Math.min(32,capacities.length);i++)if(capacities[i]>0){
            present++;mask|=1L<<i;
            if(capacities[i]>highest){highest=capacities[i];count=1;index=i;}else if(capacities[i]==highest)count++;
        }
        return present>1&&count==1?Long.toHexString(mask&~(1L<<index)):"";
    }
    static String withoutPrime(){
        return cooler(1);
    }
    static String pacingMask(){
        int[] values=new int[32];
        for(int i=0;i<values.length;i++)try(BufferedReader r=new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu"+i+"/cpu_capacity"))){values[i]=Integer.parseInt(r.readLine().trim());}catch(Exception ignored){}
        return pacingMask(values);
    }
    static String pacingMask(int[] values){
        String allowed=mask(values);if(allowed.isEmpty())return "";
        long bits=Long.parseLong(allowed,16),paced=0;int highest=0;
        for(int i=0;i<Math.min(32,values.length);i++)if((bits&(1L<<i))!=0)highest=Math.max(highest,values[i]);
        for(int i=0;i<Math.min(32,values.length);i++)if((bits&(1L<<i))!=0&&values[i]==highest)paced|=1L<<i;
        return Long.toHexString(paced);
    }
    static String secondaryPacingMask(){
        String secondary=cooler(2),little=cooler(3);
        if(secondary.isEmpty()||little.isEmpty())return "";
        return Long.toHexString(Long.parseLong(secondary,16)&~Long.parseLong(little,16));
    }
    static String cooler(int tier){
        int[] values=new int[32];
        for(int i=0;i<values.length;i++)try(BufferedReader r=new BufferedReader(new FileReader("/sys/devices/system/cpu/cpu"+i+"/cpu_capacity"))){values[i]=Integer.parseInt(r.readLine().trim());}catch(Exception ignored){}
        return coolerMask(values,tier);
    }
    static String coolerMask(int[] values,int tier){
        if(tier<=1)return mask(values);
        int highest=0;for(int v:values)highest=Math.max(highest,v);
        // Tier 2 retains the coolest performance tier plus LITTLE cores.
        // Tier 3 is requested only when one performance CPU still overheats
        // with an available GPU. It uses the lowest measured capacity tier.
        int performance=highest;
        for(int v:values)if(v>0&&(tier>=3||v*2>=highest))performance=Math.min(performance,v);
        if(performance==highest)return mask(values);
        long bits=0;for(int i=0;i<Math.min(32,values.length);i++)if(values[i]>0&&values[i]<=performance)bits|=1L<<i;
        return bits==0?"":Long.toHexString(bits);
    }
    static List<String> apply(List<String> args,String mask){
        if(mask.isEmpty())return args;
        ArrayList<String> out=new ArrayList<>(Arrays.asList("/system/bin/taskset",mask));out.addAll(args);return out;
    }
}
