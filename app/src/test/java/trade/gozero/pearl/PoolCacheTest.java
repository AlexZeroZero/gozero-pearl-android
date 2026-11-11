package trade.gozero.pearl;
import java.math.BigDecimal;
import org.junit.Test;
import static org.junit.Assert.*;
public class PoolCacheTest {
    private final String pool="51.178.73.238:3370",wallet="public-wallet";
    private String encoded()throws Exception{return PoolCache.encode(pool,wallet,new PoolAccount(new BigDecimal("0.12345678"),new BigDecimal("0.00000001"),null,null,null,Double.NaN),1000000000L);}
    @Test public void cachedPrlAmountsStayExactAndMissingFieldsStayUnknown()throws Exception{
        PoolCache.Entry c=PoolCache.decode(encoded(),pool,wallet,1000001000L);assertNotNull(c);assertEquals(new BigDecimal("0.12345679"),c.account.total());assertNull(c.account.paid);assertTrue(Double.isNaN(c.account.hashrate));assertEquals(1000000000L,c.updated);
    }
    @Test public void otherPoolOrWalletAndOldDataNeverLeak()throws Exception{
        String s=encoded();assertNull(PoolCache.decode(s,pool,"another-wallet",1000001000L));assertNull(PoolCache.decode(s,"prl.kryptex.network:7048",wallet,1000001000L));assertNull(PoolCache.decode(s,pool,wallet,2000000000L));assertNull(PoolCache.decode(s,pool,wallet,1L));
    }
    @Test public void corruptCacheIsDiscarded()throws Exception{assertNull(PoolCache.decode("bad",pool,wallet,1000001000L));assertNull(PoolCache.decode(encoded().replace("0.12345678","-100"),pool,wallet,1000001000L));}
}
