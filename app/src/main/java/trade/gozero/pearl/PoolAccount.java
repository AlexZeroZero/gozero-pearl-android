package trade.gozero.pearl;

import org.json.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;

/** Provider-specific monetary units. Null means unavailable, never a fabricated zero. */
final class PoolAccount {
    final BigDecimal unlocked,locked,paid,reward24h,threshold;
    final double hashrate;
    PoolAccount(BigDecimal u,BigDecimal l,BigDecimal p,BigDecimal r,BigDecimal t,double h){
        unlocked=u;locked=l;paid=p;reward24h=r;threshold=t;hashrate=h;
    }
    BigDecimal total(){return unlocked==null||locked==null?null:unlocked.add(locked);}
    static String amount(BigDecimal v){return v==null?"--":v.stripTrailingZeros().toPlainString();}
    static boolean supported(String endpoint){
        return PoolProvider.from(endpoint)!=PoolProvider.CUSTOM;
    }
    static BigDecimal coins(JSONObject j,String key)throws JSONException{
        if(!j.has(key)||j.isNull(key))return null;
        try{
            BigDecimal n=new BigDecimal(j.get(key).toString());
            if(n.signum()<0||n.precision()>40||n.scale()>20||n.scale() < -40)throw new NumberFormatException();
            n.toBigIntegerExact(); // Reject ambiguous fractions of an atomic unit.
            return n.movePointLeft(8);
        }catch(ArithmeticException|NumberFormatException e){throw new JSONException("Invalid PRL amount: "+key);}
    }
    static PoolAccount parse(JSONObject root,String wallet)throws JSONException{
        if(root.has("error")&&!root.isNull("error"))throw new JSONException("Pool API error");
        JSONObject s=root.getJSONObject("stats");
        if(!wallet.equals(s.getString("wallet")))throw new JSONException("Pool wallet mismatch");
        BigDecimal rewards=null;
        JSONArray periods=root.optJSONArray("rewardStats");
        if(periods!=null)for(int i=0;i<periods.length();i++){
            JSONObject p=periods.getJSONObject(i);
            if("24h".equals(p.optString("period"))){rewards=coins(p,"amount");break;}
        }
        double h=s.optDouble("hashrate",Double.NaN);
        if(!Double.isFinite(h)||h<0)h=Double.NaN;
        return new PoolAccount(coins(s,"unlocked"),coins(s,"locked"),coins(s,"paid"),rewards,coins(s,"paymentThreshold"),h);
    }
    static void validateConfig(JSONObject root)throws JSONException{
        JSONObject c=root.getJSONObject("config");
        if(!"PRL".equals(c.getString("symbol"))||new BigDecimal(c.get("coinUnits").toString()).compareTo(new BigDecimal("100000000"))!=0)
            throw new JSONException("Unexpected pool currency or units");
    }
    static BigDecimal decimal(JSONObject j,String key,boolean signed)throws JSONException{
        if(!j.has(key)||j.isNull(key))return null;
        try{
            BigDecimal n=new BigDecimal(j.get(key).toString());
            if((!signed&&n.signum()<0)||n.precision()>40||n.scale()>20||n.scale() < -40)throw new NumberFormatException();
            return n;
        }catch(NumberFormatException e){throw new JSONException("Invalid PRL amount: "+key);}
    }
    static double rate(JSONObject j,String key){double h=j.optDouble(key,Double.NaN);return Double.isFinite(h)&&h>=0?h:Double.NaN;}
    static PoolAccount kryptex(JSONObject balance,JSONObject payouts,JSONObject workers,BigDecimal defaultThreshold)throws JSONException{
        BigDecimal threshold=decimal(balance,"threshold",false);
        if(threshold==null||threshold.signum()==0)threshold=defaultThreshold;
        double rate=Double.NaN;
        if(workers!=null){
            JSONArray rows=workers.optJSONArray("results");
            // Do not present a partial paginated response as the miner's total rate.
            if(rows!=null&&workers.optInt("count",rows.length())==rows.length()){
                rate=0;for(int i=0;i<rows.length();i++)rate+=rate(rows.getJSONObject(i),"hashrate");
            }
        }
        return new PoolAccount(decimal(balance,"confirmed",false),decimal(balance,"unconfirmed",false),payouts==null?null:decimal(payouts,"paid",false),null,threshold,rate);
    }
    private static BigDecimal pearlAmounts(JSONArray rows)throws JSONException{
        if(rows==null)return null;
        BigDecimal total=BigDecimal.ZERO;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);
            if("pearl".equals(row.optString("coin_type","pearl"))){BigDecimal amount=decimal(row,"amount",true);if(amount==null)throw new JSONException("Missing Pearl amount");total=total.add(amount);}
        }
        return total;
    }
    static PoolAccount pearlHash(JSONObject j,String wallet,BigDecimal threshold)throws JSONException{
        if(j.has("error")&&!j.isNull("error"))throw new JSONException("Pool API error");
        // Current API routes by wallet; validate an echoed identity if provided.
        for(String key:new String[]{"address","wallet"})if(j.has(key)&&!wallet.equals(j.getString(key)))throw new JSONException("Pool wallet mismatch");
        JSONArray balances=j.optJSONArray("balance_transactions"),workers=j.optJSONArray("connected_workers");
        if(balances==null&&workers==null&&!j.has("pending_rewards"))throw new JSONException("Unknown PearlHash account schema");
        JSONObject pending=j.optJSONObject("pending_rewards");
        double h=Double.NaN;
        if(workers!=null){h=0;for(int i=0;i<workers.length();i++){JSONArray g=workers.getJSONObject(i).optJSONArray("gpu_info");if(g==null){h=Double.NaN;break;}for(int k=0;k<g.length();k++)h+=rate(g.getJSONObject(k),"hashrate");}}
        return new PoolAccount(pearlAmounts(balances),pending==null?null:pearlAmounts(pending.optJSONArray("epochs")),null,null,threshold,h);
    }
}
