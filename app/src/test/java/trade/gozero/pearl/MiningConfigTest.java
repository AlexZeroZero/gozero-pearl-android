package trade.gozero.pearl;
import org.junit.Test;
import static org.junit.Assert.*;

public class MiningConfigTest {
    @Test public void addressRequiresChecksumAndMainnet(){
        String valid=encode("prl");assertTrue(MiningConfig.address(valid));assertTrue(MiningConfig.address(valid.toUpperCase()));
        assertFalse(MiningConfig.address(valid.substring(0,62)+(valid.endsWith("q")?"p":"q")));
        assertFalse(MiningConfig.address("Prl"+valid.substring(3)));assertFalse(MiningConfig.address(encode("tprl")));
        assertFalse(MiningConfig.address("prl1p"));assertFalse(MiningConfig.address(null));
    }
    @Test public void poolNormalization(){assertEquals("stratum+tcp://example.org:3370",MiningConfig.pool("example.org:3370"));assertEquals("stratum+tcp://[::1]:3370",MiningConfig.pool("stratum+tcp://[::1]:3370"));}
    @Test public void rejectsInvalidEndpoints(){for(String s:new String[]{"example.org","http://example.org:123","stratum+ssl://example.org:443","x:65536","x:0","x:1/path","x:1?pass=secret","user@x:1","x:1\n--wallet"}){try{MiningConfig.pool(s);fail(s);}catch(IllegalArgumentException expected){}}}
    @Test public void workersAreArgumentsNotShell(){assertTrue(MiningConfig.worker("GT5_01"));assertFalse(MiningConfig.worker("x;reboot"));assertFalse(MiningConfig.worker("--wallet other"));}
    @Test public void balanceProxyIsOptionalAndBounded(){
        assertEquals("",MiningConfig.dataProxy("  "));assertEquals("http://192.0.2.1:7897",MiningConfig.dataProxy("192.0.2.1:7897"));
        for(String s:new String[]{"https://host:443","socks5://host:1080","host:0","host:99999","user:pass@host:80","host:80/path","host:80?x=1"})try{MiningConfig.dataProxy(s);fail(s);}catch(IllegalArgumentException expected){}
    }
    @Test public void boundedMemoryProfiles(){assertTrue(MiningConfig.validSize(4));assertTrue(MiningConfig.validSize(32));assertFalse(MiningConfig.validSize(128));assertTrue(MiningConfig.memoryBudgetMiB(32)>MiningConfig.memoryBudgetMiB(4));}
    @Test public void matrixMigrationAndBounds(){
        assertEquals("16x16",MiningConfig.matrix("",16));assertEquals("8x8",MiningConfig.matrix("999x999",999));
        assertEquals("1x64",MiningConfig.matrix("1x64",8));assertEquals(1,MiningConfig.rows("1x64"));assertEquals(64,MiningConfig.columns("1x64"));
        for(String profile:MiningConfig.MATRICES){assertTrue(MiningConfig.memoryBudgetMiB(profile)>=192);assertTrue(MiningConfig.memoryBudgetMiB(profile)<=1280);}
        assertTrue(MiningConfig.memoryBudgetMiB("1x128")>MiningConfig.memoryBudgetMiB("1x64"));
    }
    @Test public void gpuWorkerStaysValid(){assertEquals("GT5_gpu",MiningConfig.gpuWorker("GT5"));String name=MiningConfig.gpuWorker("abcdefghijklmnopqrstuvwxyz123456");assertEquals(32,name.length());assertTrue(MiningConfig.worker(name));}
    private static String encode(String hrp){String chars="qpzry9x8gf2tvdw0s3jn54khce6mua7l";int chk=1;for(char c:hrp.toCharArray())chk=step(chk)^(c>>5);chk=step(chk);for(char c:hrp.toCharArray())chk=step(chk)^(c&31);StringBuilder b=new StringBuilder(hrp+"1p");chk=step(chk)^1;for(int i=0;i<52;i++){b.append('q');chk=step(chk);}for(int i=0;i<6;i++)chk=step(chk);chk^=0x2bc830a3;for(int i=0;i<6;i++)b.append(chars.charAt((chk>>>(5*(5-i)))&31));return b.toString();}
    private static int step(int n){int b=n>>>25,v=(n&0x1ffffff)<<5;int[] g={0x3b6a57b2,0x26508e6d,0x1ea119fa,0x3d4233dd,0x2a1462b3};for(int i=0;i<5;i++)if(((b>>>i)&1)!=0)v^=g[i];return v;}
}
