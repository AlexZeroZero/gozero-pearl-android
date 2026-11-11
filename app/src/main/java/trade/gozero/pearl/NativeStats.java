package trade.gozero.pearl;

import java.io.*;
import java.net.*;
import org.json.*;

final class NativeStats {
    final double rate,average;
    final int rssMiB,accepted,rejected;
    final boolean connected;
    private NativeStats(JSONObject j)throws JSONException{
        JSONArray rates=j.getJSONObject("hashrate").getJSONArray("total");
        rate=finite(rates.getDouble(0));average=finite(rates.getDouble(1));
        rssMiB=(int)Math.max(0,j.optLong("rss_kib")/1024);
        connected=j.optBoolean("pool_connected");JSONObject shares=j.getJSONObject("shares");
        accepted=Math.max(0,shares.getInt("accepted"));rejected=Math.max(0,shares.getInt("rejected"));
    }
    private static double finite(double n){return Double.isFinite(n)?Math.max(0,n):0;}
    static NativeStats read(int port)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+"/summary").openConnection(Proxy.NO_PROXY);
        try{
            c.setConnectTimeout(350);c.setReadTimeout(350);c.setInstanceFollowRedirects(false);
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] chunk=new byte[2048];int n;while((n=in.read(chunk))!=-1){b.write(chunk,0,n);if(b.size()>32768)throw new IOException("Stats too large");}}
            return new NativeStats(new JSONObject(b.toString("UTF-8")));
        }finally{c.disconnect();}
    }
}
