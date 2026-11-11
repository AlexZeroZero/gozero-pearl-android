package trade.gozero.pearl;

import android.os.SystemClock;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Foreground refresh only. Immutable snapshots keep old balances labelled on failures. */
final class PoolStats {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    static final class Snapshot {
        final PoolAccount account;final PoolOverview overview;final String state,wallet;final long updated,elapsed;final boolean loading;
        Snapshot(PoolAccount a,PoolOverview o,String s,String w,long u,long e,boolean l){account=a;overview=o;state=s;wallet=w;updated=u;elapsed=e;loading=l;}
    }
    private static String key="",wallet="",state="pool_wait";
    private static PoolAccount account;
    private static PoolOverview overview;
    private static long updated,elapsed,next,lastStart;
    private static boolean busy;
    private static SharedPreferences cache;
    private static String route="";
    static synchronized void init(Context context){cache=context.getApplicationContext().getSharedPreferences("pool-cache",0);}
    static synchronized Snapshot snapshot(){return new Snapshot(account,overview,state,wallet,updated,elapsed,busy);}
    static synchronized void request(String pool,String address,String dataProxy,boolean force){
        String identity=pool+"|"+address;
        if(!dataProxy.equals(route)){route=dataProxy;next=0;}
        if(!identity.equals(key)){
            key=identity;wallet=address;account=null;overview=null;updated=elapsed=next=0;state="pool_wait";
            PoolCache.Entry saved=cache==null?null:PoolCache.decode(cache.getString("last",""),pool,address,System.currentTimeMillis());
            if(saved!=null){account=saved.account;updated=saved.updated;elapsed=SystemClock.elapsedRealtime()-Math.max(0,System.currentTimeMillis()-updated);state="pool_stale";}
        }
        if(!MiningConfig.address(address)){state="needwallet";return;}
        if(!PoolAccount.supported(pool)){state="pool_unsupported";return;}
        long now=SystemClock.elapsedRealtime();
        if(busy||(!force&&now<next)||(force&&now-lastStart<5000))return;
        busy=true;lastStart=now;
        IO.execute(()->{
            PoolAccount fetched=null;PoolOverview info=null;String result="pool_fresh";
            PoolProvider provider=PoolProvider.from(pool);
            try{
                String a=URLEncoder.encode(address,"UTF-8"),base=provider.api;
                if(provider==PoolProvider.LUCKY){
                    info=PoolOverview.lucky(get(base+"/stats?v=2",dataProxy));
                    fetched=PoolAccount.parse(get(base+"/stats_address?address="+a,dataProxy),address);
                }else if(provider==PoolProvider.PEARLHASH){
                    info=PoolOverview.pearlHash(get(base+"/stats",dataProxy),get(base+"/chain-info",dataProxy));
                    try{fetched=PoolAccount.pearlHash(get(base+"/account/"+a,dataProxy),address,info.threshold);}
                    catch(HttpFailure e){if(e.code==404)result="pool_no_account";else throw e;}
                }else if(provider==PoolProvider.KRYPTEX){
                    info=PoolOverview.kryptex(get(base+"/v1/pool/info",dataProxy));
                    JSONObject balance=get(base+"/v1/miner/balance/"+a,dataProxy);
                    // Auxiliary statistics must not hide an otherwise valid balance.
                    JSONObject payouts=null,workers=null;
                    try{payouts=get(base+"/v1/miner/payouts/"+a+"/stats",dataProxy);workers=get(base+"/v3/miner/workers/"+a,dataProxy);}catch(Exception e){result="pool_partial";}
                    fetched=PoolAccount.kryptex(balance,payouts,workers,info.threshold);
                }
            }catch(Exception e){result="pool_error";State.log("[pool-api] "+e.getClass().getSimpleName()+": "+e.getMessage());}
            synchronized(PoolStats.class){
                busy=false;
                if(!identity.equals(key))return; // Never display a previous wallet's late response.
                state=result;next=SystemClock.elapsedRealtime()+30000;
                if(info!=null)overview=info;
                if("pool_no_account".equals(result))account=null;
                if(fetched!=null||"pool_no_account".equals(result)){
                    account=fetched;updated=System.currentTimeMillis();elapsed=SystemClock.elapsedRealtime();
                    if(cache!=null)try{if(fetched==null)cache.edit().remove("last").apply();else cache.edit().putString("last",PoolCache.encode(pool,address,fetched,updated)).apply();}catch(Exception ignored){}
                }
            }
        });
    }
    private static JSONObject get(String url,String dataProxy)throws Exception{
        try{return fetch(url,dataProxy);}
        catch(IOException e){
            if(dataProxy.isEmpty()||(e instanceof HttpFailure&&((HttpFailure)e).code==404))throw e;
            State.log("[pool-api] Proxy connection failed; retrying public data on phone network");
            return fetch(url,"");
        }
    }
    private static final class HttpFailure extends IOException {final int code;HttpFailure(int code){super("Pool API HTTP "+code);this.code=code;}}
    private static JSONObject fetch(String address,String dataProxy)throws Exception{
        String setting=MiningConfig.dataProxy(dataProxy);
        URL url=new URL(address);
        // HTTP CONNECT tunnels HTTPS; normal certificate and hostname checks remain enabled.
        // This affects public account data only, never the native Stratum connection.
        HttpURLConnection c;
        if(setting.isEmpty())c=(HttpURLConnection)url.openConnection(Proxy.NO_PROXY);
        else{URI p=URI.create(setting);c=(HttpURLConnection)url.openConnection(new Proxy(Proxy.Type.HTTP,new InetSocketAddress(p.getHost(),p.getPort())));}
        c.setConnectTimeout(6000);c.setReadTimeout(6000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","Mozilla/5.0 GozeroPearl/1.0 (Android)");
        try{
            if(c.getResponseCode()!=200)throw new HttpFailure(c.getResponseCode());
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){
                byte[] buf=new byte[8192];int n;long deadline=SystemClock.elapsedRealtime()+10000;
                while((n=in.read(buf))!=-1){
                    if(b.size()+n>2*1024*1024||SystemClock.elapsedRealtime()>deadline)throw new IOException("Pool response limit");
                    b.write(buf,0,n);
                }
            }
            return new JSONObject(b.toString("UTF-8"));
        }finally{c.disconnect();}
    }
}
