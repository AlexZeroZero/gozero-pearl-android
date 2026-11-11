package trade.gozero.pearl;

import java.net.URI;
import java.util.Locale;

/** Validate user input before it becomes a native process argument. */
final class MiningConfig {
    static final int[] SIZES={4,8,16,32};
    static int memoryBudgetMiB(int size){return 128+size*32;}
    static boolean validSize(int size){for(int n:SIZES)if(n==size)return true;return false;}
    static final String[] MATRICES={"4x4","8x8","16x16","32x32","4x16","2x32","1x64","1x128"};
    static String matrix(String value,int legacySize){
        for(String v:MATRICES)if(v.equals(value))return v;
        int size=validSize(legacySize)?legacySize:8;
        return size+"x"+size;
    }
    static int rows(String profile){return Integer.parseInt(matrix(profile,8).split("x")[0]);}
    static int columns(String profile){return Integer.parseInt(matrix(profile,8).split("x")[1]);}
    // Include staging/proof headroom. Actual resident usage is shown separately.
    static int memoryBudgetMiB(String profile){return 128+8*(rows(profile)+columns(profile));}
    static String matrixLabel(String profile){return rows(profile)+"K × "+columns(profile)+"K";}
    static String gpuWorker(String worker){return worker.substring(0,Math.min(worker.length(),28))+"_gpu";}
    static String pool(String value){
        String v=value.trim();
        if(!v.contains("://"))v="stratum+tcp://"+v;
        URI u=URI.create(v);
        if(!"stratum+tcp".equals(u.getScheme())||u.getHost()==null||u.getPort()<1||u.getPort()>65535
                ||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null
                ||(u.getPath()!=null&&!u.getPath().isEmpty()))throw new IllegalArgumentException("pool");
        if(v.length()>250)throw new IllegalArgumentException("pool");
        return v;
    }
    static boolean worker(String s){return s.matches("[A-Za-z0-9_-]{1,32}");}
    static String dataProxy(String value){
        String v=value.trim();if(v.isEmpty())return "";
        URI u=URI.create(v.contains("://")?v:"http://"+v);
        if(!"http".equals(u.getScheme())||u.getHost()==null||u.getPort()<1||u.getPort()>65535||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||
            (u.getPath()!=null&&!u.getPath().isEmpty())||v.length()>250)throw new IllegalArgumentException("proxy");
        return u.toString();
    }
    static boolean address(String value){
        if(value==null||value.length()!=63)return false;
        if(!value.equals(value.toLowerCase(Locale.ROOT))&&!value.equals(value.toUpperCase(Locale.ROOT)))return false;
        String s=value.toLowerCase(Locale.ROOT);
        if(!s.startsWith("prl1p"))return false;
        int chk=1;
        String hrp="prl";
        for(int i=0;i<hrp.length();i++)chk=step(chk)^(hrp.charAt(i)>>5);
        chk=step(chk);
        for(int i=0;i<hrp.length();i++)chk=step(chk)^(hrp.charAt(i)&31);
        String charset="qpzry9x8gf2tvdw0s3jn54khce6mua7l";
        int acc=0,bits=0,bytes=0;
        for(int i=4;i<s.length();i++){
            int v=charset.indexOf(s.charAt(i));if(v<0)return false;
            chk=step(chk)^v;
            if(i>4&&i<s.length()-6){acc=((acc<<5)|v)&0xffff;bits+=5;if(bits>=8){bits-=8;bytes++;}}
        }
        return chk==0x2bc830a3&&bytes==32&&bits<5&&((acc<<(8-bits))&255)==0;
    }
    private static int step(int chk){
        int top=chk>>>25;int r=(chk&0x1ffffff)<<5;
        int[] g={0x3b6a57b2,0x26508e6d,0x1ea119fa,0x3d4233dd,0x2a1462b3};
        for(int i=0;i<5;i++)if(((top>>>i)&1)!=0)r^=g[i];return r;
    }
}
