package trade.gozero.pearl;

import org.json.*;
import java.math.BigDecimal;

final class PoolOverview {
    final double hashrate;final long miners,workers;final BigDecimal fee,threshold;
    PoolOverview(double h,long m,long w,BigDecimal f,BigDecimal t){hashrate=h;miners=m;workers=w;fee=f;threshold=t;}
    static PoolOverview kryptex(JSONObject j)throws JSONException{
        if(!"prl".equalsIgnoreCase(j.getString("ticker")))throw new JSONException("Unexpected pool currency");
        BigDecimal threshold=PoolAccount.decimal(j,"defpay",false);
        if(threshold==null||threshold.signum()==0)threshold=PoolAccount.decimal(j,"minpay",false);
        BigDecimal fee=PoolAccount.decimal(j,"fee",false);
        return new PoolOverview(PoolAccount.rate(j,"hashrate"),j.optLong("miners",-1),j.optLong("workers",-1),fee==null?null:fee.movePointRight(2),threshold);
    }
    static PoolOverview pearlHash(JSONObject j,JSONObject chain)throws JSONException{
        if(!"main".equals(chain.getString("chain")))throw new JSONException("Expected Pearl mainnet");
        // Fee and minimum payout from the provider's published connection guide.
        return new PoolOverview(PoolAccount.rate(j,"hashrate"),j.optLong("total_accounts",-1),j.optLong("total_workers",-1),BigDecimal.ONE,BigDecimal.ONE);
    }
    static PoolOverview lucky(JSONObject j)throws JSONException{
        PoolAccount.validateConfig(j);JSONObject p=j.optJSONObject("pool"),c=j.getJSONObject("config");
        return new PoolOverview(p==null?Double.NaN:PoolAccount.rate(p,"hashrate"),p==null?-1:p.optLong("miners",-1),p==null?-1:p.optLong("workers",-1),PoolAccount.decimal(c,"fee",false),PoolAccount.coins(c,"minPaymentThreshold"));
    }
}
