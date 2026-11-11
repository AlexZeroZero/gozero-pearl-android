package trade.gozero.pearl;

import java.net.URI;
import java.util.Locale;

/** Only recognised PRL endpoints may select an account API. */
enum PoolProvider {
    LUCKY("LuckyPool","https://pearl.luckypool.io/api","https://pearl.luckypool.io/miner-stats/"),
    PEARLHASH("PearlHash","https://pearlhash.xyz/api","https://pearlhash.xyz/account/"),
    KRYPTEX("Kryptex","https://pool.kryptex.com/prl/api","https://pool.kryptex.com/en/prl/miner/stats/"),
    CUSTOM("Custom","","");
    final String title,api,accountUrl;
    PoolProvider(String title,String api,String accountUrl){this.title=title;this.api=api;this.accountUrl=accountUrl;}
    static final String[] LABELS={"LuckyPool · CPU EU","LuckyPool · Singapore","PearlHash · Hong Kong","PearlHash · Global","PearlHash · Global 2","Kryptex · Hong Kong","Kryptex · Singapore","Kryptex · Global"};
    static final String[] ENDPOINTS={"pearl-cpu-eu1.luckypool.io:3370","pearl-sg.lproute.com:3360","hk.pearlhash.net:9000","pool.pearlhash.xyz:9000","global2.pearlhash.net:9000","prl-hk.kryptex.network:7048","prl-sg.kryptex.network:7048","prl.kryptex.network:7048"};
    static PoolProvider from(String endpoint){
        try{
            String h=URI.create(MiningConfig.pool(endpoint)).getHost().toLowerCase(Locale.ROOT);
            if(h.equals("51.178.73.238")||(h.startsWith("pearl-")&&(h.endsWith(".luckypool.io")||h.endsWith(".lproute.com"))))return LUCKY;
            if(h.equals("pool.pearlhash.xyz")||h.equals("global.pearlhash.net")||h.equals("global2.pearlhash.net")||h.equals("hk.pearlhash.net"))return PEARLHASH;
            if(h.equals("prl.kryptex.network")||h.matches("prl-(hk|sg|eu|us|br|ru|ae)\\.kryptex\\.network"))return KRYPTEX;
        }catch(Exception ignored){}
        return CUSTOM;
    }
    static int preset(String endpoint){
        try{String p=MiningConfig.pool(endpoint);for(int i=0;i<ENDPOINTS.length;i++)if(p.equals(MiningConfig.pool(ENDPOINTS[i])))return i;}catch(Exception ignored){}
        return ENDPOINTS.length;
    }
}
