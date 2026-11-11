package trade.gozero.pearl;
import org.json.*;

/** Persist the last verified public balance, strictly scoped to pool and wallet. */
final class PoolCache {
    static final class Entry {final PoolAccount account;final long updated;Entry(PoolAccount a,long u){account=a;updated=u;}}
    static String encode(String pool,String wallet,PoolAccount a,long updated)throws JSONException{
        JSONObject j=new JSONObject().put("pool",pool).put("wallet",wallet).put("symbol","PRL").put("updated",updated);
        j.put("unlocked",a.unlocked==null?JSONObject.NULL:a.unlocked.toPlainString());j.put("locked",a.locked==null?JSONObject.NULL:a.locked.toPlainString());
        j.put("paid",a.paid==null?JSONObject.NULL:a.paid.toPlainString());j.put("reward24h",a.reward24h==null?JSONObject.NULL:a.reward24h.toPlainString());j.put("threshold",a.threshold==null?JSONObject.NULL:a.threshold.toPlainString());
        if(Double.isFinite(a.hashrate))j.put("hashrate",a.hashrate);return j.toString();
    }
    static Entry decode(String raw,String pool,String wallet,long now){
        try{
            JSONObject j=new JSONObject(raw);long updated=j.getLong("updated");
            if(!pool.equals(j.getString("pool"))||!wallet.equals(j.getString("wallet"))||!"PRL".equals(j.getString("symbol"))||updated<=0||updated>now+60000||now-updated>7L*86400000)return null;
            PoolAccount a=new PoolAccount(PoolAccount.decimal(j,"unlocked",PoolProvider.from(pool)==PoolProvider.PEARLHASH),PoolAccount.decimal(j,"locked",false),PoolAccount.decimal(j,"paid",false),PoolAccount.decimal(j,"reward24h",false),PoolAccount.decimal(j,"threshold",false),PoolAccount.rate(j,"hashrate"));
            return new Entry(a,updated);
        }catch(Exception e){return null;}
    }
}
