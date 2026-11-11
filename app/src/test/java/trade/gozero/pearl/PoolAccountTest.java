package trade.gozero.pearl;

import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class PoolAccountTest {
    private JSONObject data()throws Exception{
        return new JSONObject("{\"stats\":{\"wallet\":\"test-wallet\",\"unlocked\":\"1\",\"locked\":123456789,\"paid\":\"9007199254740993\",\"paymentThreshold\":500000000,\"hashrate\":125000000000},\"rewardStats\":[{\"period\":\"1h\",\"amount\":99},{\"period\":\"24h\",\"amount\":123}]}");
    }
    @Test public void atomicUnitsRetainDustAndLargeIntegerPrecision()throws Exception{
        PoolAccount a=PoolAccount.parse(data(),"test-wallet");
        assertEquals("0.00000001",PoolAccount.amount(a.unlocked));
        assertEquals("1.2345679",PoolAccount.amount(a.total()));
        assertEquals("90071992.54740993",PoolAccount.amount(a.paid));
        assertEquals("0.00000123",PoolAccount.amount(a.reward24h));
        assertEquals("5",PoolAccount.amount(a.threshold));
        assertEquals(125e9,a.hashrate,0);
    }
    @Test public void missingAmountsAreUnknownNotZero()throws Exception{
        PoolAccount a=PoolAccount.parse(new JSONObject("{\"stats\":{\"wallet\":\"test-wallet\"}}"),"test-wallet");
        assertNull(a.total());assertNull(a.reward24h);assertEquals("--",PoolAccount.amount(a.paid));assertTrue(Double.isNaN(a.hashrate));
    }
    @Test public void genuineZeroIsShownAsZero()throws Exception{
        JSONObject d=data();d.getJSONObject("stats").put("unlocked",0);
        assertEquals("0",PoolAccount.amount(PoolAccount.parse(d,"test-wallet").unlocked));
    }
    @Test public void rejectsDifferentWalletAndApiErrors()throws Exception{
        for(JSONObject d:new JSONObject[]{data(),new JSONObject("{\"error\":\"not found\"}"),new JSONObject("{}")}){
            try{PoolAccount.parse(d,"different-wallet");fail();}catch(JSONException expected){}
        }
    }
    @Test public void malformedAmountsDoNotBecomeZero()throws Exception{
        for(String v:new String[]{"NaN","-1","0.1","","1e1000000000"}){
            JSONObject d=data();d.getJSONObject("stats").put("unlocked",v);
            try{PoolAccount.parse(d,"test-wallet");fail(v);}catch(JSONException expected){}
        }
    }
    @Test public void validatesCurrencyAndUnits()throws Exception{
        PoolAccount.validateConfig(new JSONObject("{\"config\":{\"symbol\":\"PRL\",\"coinUnits\":100000000}}"));
        for(String s:new String[]{"{\"symbol\":\"NOCK\",\"coinUnits\":100000000}","{\"symbol\":\"PRL\",\"coinUnits\":1000}"}){
            try{PoolAccount.validateConfig(new JSONObject().put("config",new JSONObject(s)));fail();}catch(JSONException expected){}
        }
    }
    @Test public void poolMappingDoesNotLeakBalancesAcrossProviders(){
        assertTrue(PoolAccount.supported("pearl-cpu-eu1.luckypool.io:3370"));
        assertTrue(PoolAccount.supported("stratum+tcp://51.178.73.238:3370"));
        assertTrue(PoolAccount.supported("pearl-sg.lproute.com:3360"));
        assertTrue(PoolAccount.supported("prl.kryptex.network:7048"));
        assertTrue(PoolAccount.supported("hk.pearlhash.net:9000"));
        for(String s:new String[]{"other.luckypool.io:1","luckypool.io.evil.example:1","pearl-x.luckypool.io.evil.example:1","prl.kryptex.network.evil.example:7048","btc.kryptex.network:1","hk.pearlhash.net.evil.example:9000",""})assertFalse(s,PoolAccount.supported(s));
    }
    @Test public void kryptexAmountsAreCoinsNotAtoms()throws Exception{
        PoolAccount a=PoolAccount.kryptex(new JSONObject("{\"confirmed\":1.23456789,\"unconfirmed\":0.00000001,\"threshold\":0}"),new JSONObject("{\"paid\":45.678}"),new JSONObject("{\"count\":2,\"results\":[{\"hashrate\":2},{\"hashrate\":3}]}"),java.math.BigDecimal.ONE);
        assertEquals("1.2345679",PoolAccount.amount(a.total()));assertEquals("45.678",PoolAccount.amount(a.paid));assertEquals("1",PoolAccount.amount(a.threshold));assertEquals(5,a.hashrate,0);assertNull(a.reward24h);
    }
    @Test public void kryptexRejectsWrongCurrencyAndPartialWorkerTotal()throws Exception{
        try{PoolOverview.kryptex(new JSONObject("{\"ticker\":\"btc\"}"));fail();}catch(JSONException expected){}
        PoolAccount a=PoolAccount.kryptex(new JSONObject(),null,new JSONObject("{\"count\":8,\"results\":[{\"hashrate\":2}]}"),null);
        assertNull(a.total());assertTrue(Double.isNaN(a.hashrate));
    }
    @Test public void pearlHashBalancesUseSignedPrlTransactionsAndExcludeOtherCoins()throws Exception{
        PoolAccount a=PoolAccount.pearlHash(new JSONObject("{\"balance_transactions\":[{\"amount\":2.12345678},{\"amount\":-1},{\"coin_type\":\"other\",\"amount\":99}],\"pending_rewards\":{\"epochs\":[{\"coin_type\":\"pearl\",\"amount\":0.00000001}]},\"connected_workers\":[{\"gpu_info\":[{\"hashrate\":25},{\"hashrate\":30}]}]}"),"wallet",java.math.BigDecimal.ONE);
        assertEquals("1.12345678",PoolAccount.amount(a.unlocked));assertEquals("1.12345679",PoolAccount.amount(a.total()));assertEquals(55,a.hashrate,0);assertNull(a.paid);assertNull(a.reward24h);
    }
    @Test public void pearlHashUnknownDoesNotBecomeZero()throws Exception{
        for(String s:new String[]{"{}","{\"error\":\"not found\"}","{\"wallet\":\"different\",\"connected_workers\":[]}"})try{PoolAccount.pearlHash(new JSONObject(s),"wallet",null);fail(s);}catch(JSONException expected){}
        PoolAccount a=PoolAccount.pearlHash(new JSONObject("{\"connected_workers\":[]}"),"wallet",null);assertNull(a.total());assertEquals(0,a.hashrate,0);
        try{PoolOverview.pearlHash(new JSONObject(),new JSONObject("{\"chain\":\"test\"}"));fail();}catch(JSONException expected){}
    }
    @Test public void decimalLimitsAvoidUnboundedExponentOrNonFiniteAmounts()throws Exception{
        for(String s:new String[]{"NaN","1e999999999","-0.01",""})try{PoolAccount.decimal(new JSONObject().put("amount",s),"amount",false);fail(s);}catch(JSONException expected){}
    }
}
