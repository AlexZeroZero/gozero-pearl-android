package trade.gozero.pearl;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private int BG,PANEL,LINE,INK,MUTED,ACCENT,GREEN,FIELD;
    private boolean light,follow=true,errorOnly;
    private int page;
    private LinearLayout root,body,dock;
    private ScrollView scroll,logScroll;
    private TextView rate,average,status,threadsValue,ramValue,cpuValue,batteryValue,acceptedValue,rejectedValue,sessionValue,poolState,logView,proofValue,lastBench;
    private TextView resourceDetail,backendDetail,gpuValue,temperatureDetail,thermalNotice;
    private TextView fundsTotal,fundsUnlocked,fundsLocked,fundsPaid,fundsReward,fundsMeta,fundsStatus,poolOverview;
    private Button action;
    private EditText pool,wallet,worker,dataProxy;
    private Spinner threads,size,cpuLimit,batLimit;
    private Switch charge,gpu,autoCpu;
    private Sparkline chart;
    private Mini mini;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String lastLog="";
    private SharedPreferences prefs(){return getSharedPreferences("app",0);}
    private String matrixPreference(){return MiningConfig.matrix(prefs().getString("matrix",""),prefs().getInt("size",8));}
    private String t(String k){return Words.t(this,k);}
    private final Runnable tick=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle b){super.onCreate(b);PoolStats.init(this);State.verified=MiningService.verificationKey(matrixPreference()).equals(prefs().getString("verifiedCore",""));State.lastBenchmark=prefs().getFloat("lastBenchmark",0);build();}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(tick);tick.run();}
    @Override protected void onPause(){if(!isInPictureInPictureMode())handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onStop(){handler.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(!isInPictureInPictureMode())rebuild(null);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private void theme(){
        light=prefs().getBoolean("light",false);setTheme(light?R.style.AppThemeLight:R.style.AppTheme);
        BG=light?0xfff5f3fa:0xff0d0b12;PANEL=light?0xffffffff:0xff18141f;LINE=light?0xffe2dbea:0xff30263e;
        INK=light?0xff21192e:0xfff2edf8;MUTED=light?0xff776c85:0xff9c91ad;ACCENT=light?0xff7048b5:0xffbc9bff;
        GREEN=light?0xff137b5c:0xff7bd8b5;FIELD=light?0xfff1edf6:0xff100d17;
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(light?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0);
    }
    private GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private GradientDrawable border(int color,int radius){GradientDrawable g=shape(color,radius);g.setStroke(dp(1),LINE);return g;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setIncludeFontPadding(false);v.setFontFeatureSettings("tnum");if(bold)v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    private void add(LinearLayout parent,View v,int w,int h){parent.addView(v,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));}
    private void gap(LinearLayout l,int n){add(l,new View(this),1,n);}
    private void weight(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(border(PANEL,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);body.addView(c,p);return c;}
    private void heading(LinearLayout l,String title){TextView v=text(title,13,MUTED,true);l.addView(v);gap(l,10);}
    private Button button(String title,boolean primary,Runnable fn){Button b=new Button(this);b.setText(title);b.setAllCaps(false);b.setTextSize(13);b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));b.setTextColor(primary?(light?0xffffffff:0xff21122e):INK);b.setBackground(primary?shape(ACCENT,12):border(FIELD,12));b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setPadding(dp(10),dp(8),dp(10),dp(8));b.setMaxLines(2);b.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);b.setOnClickListener(v->fn.run());return b;}
    private void build(){
        resourceDetail=backendDetail=gpuValue=temperatureDetail=thermalNotice=null;
        fundsTotal=fundsUnlocked=fundsLocked=fundsPaid=fundsReward=fundsMeta=fundsStatus=poolOverview=null;
        theme();rate=average=status=threadsValue=ramValue=cpuValue=batteryValue=acceptedValue=rejectedValue=sessionValue=poolState=logView=proofValue=lastBench=null;chart=null;pool=wallet=worker=null;
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(BG);root=column();frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        root.setPadding(dp(16),dp(8),dp(16),dp(8));
        if(Build.VERSION.SDK_INT>=35)root.setOnApplyWindowInsetsListener((v,in)->{android.graphics.Insets bars=in.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(16)+bars.left,dp(8)+bars.top,dp(16)+bars.right,dp(8)+bars.bottom);return in;});
        LinearLayout header=row();Logo logo=new Logo();add(header,logo,40,40);Space space=new Space(this);add(header,space,10,1);
        LinearLayout brand=column();brand.addView(text("GOZERO",10,MUTED,true));gap(brand,3);brand.addView(text("Pearl",23,INK,true));weight(header,brand);
        TextView badge=text("PRL  /  1.0",10,ACCENT,true);badge.setPadding(dp(10),dp(7),dp(10),dp(7));badge.setBackground(border(FIELD,9));header.addView(badge);add(root,header,-1,52);gap(root,10);
        LinearLayout tabs=row();tabs.setPadding(dp(4),dp(4),dp(4),dp(4));tabs.setBackground(border(FIELD,14));String[] keys={"home","core","logs","settings"};
        for(int i=0;i<4;i++){final int n=i;Button b=button(t(keys[i]),false,()->{page=n;build();});b.setBackground(shape(i==page?PANEL:Color.TRANSPARENT,10));b.setTextColor(i==page?ACCENT:MUTED);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);tabs.addView(b,p);}root.addView(tabs);gap(root,12);
        body=column();
        if(page==2){root.addView(body,new LinearLayout.LayoutParams(-1,0,1));logs();}
        else{scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));if(page==0)dashboard();else if(page==1)core();else settings();}
        dock=row();dock.setPadding(0,dp(8),0,0);LinearLayout hint=column();hint.addView(text("PEARL · CPU",10,ACCENT,true));gap(hint,4);hint.addView(text("ARM64 / V3",10,MUTED,false));weight(dock,hint);
        action=button(t(State.active?"stop":"start"),true,()->{if(State.active)stop();else start("mine");});add(dock,action,180,48);add(root,dock,-1,60);
        mini=new Mini();mini.setVisibility(View.GONE);frame.addView(mini,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);refresh();
    }
    private TextView metric(LinearLayout parent,String label,String value){LinearLayout c=column();c.addView(text(t(label),11,MUTED,false));gap(c,7);TextView v=text(value,19,INK,true);c.addView(v);weight(parent,c);return v;}
    private void dashboard(){
        LinearLayout c=card();LinearLayout top=row();weight(top,text(t("live"),13,MUTED,true));status=text(t(State.status),11,GREEN,true);top.addView(status);c.addView(top);gap(c,8);
        thermalNotice=text("",12,light?0xff9b4d00:0xffffc077,true);thermalNotice.setPadding(dp(10),dp(8),dp(10),dp(8));thermalNotice.setLineSpacing(dp(2),1);thermalNotice.setBackground(shape(light?0xffffefdc:0xff34271b,9));thermalNotice.setMinHeight(dp(54));c.addView(thermalNotice);gap(c,7);
        LinearLayout number=row();rate=text("0.00",48,INK,true);number.addView(rate);TextView unit=text("  GH/s",14,ACCENT,true);number.addView(unit);c.addView(number);gap(c,3);
        c.addView(text(t("unitnote"),10,MUTED,false));chart=new Sparkline();add(c,chart,-1,74);
        LinearLayout avgRow=row();weight(avgRow,text(t("avg"),11,MUTED,false));average=text("0.00 GH/s",12,ACCENT,true);avgRow.addView(average);c.addView(avgRow);gap(c,8);backendDetail=text("",10,MUTED,false);c.addView(backendDetail);
        LinearLayout resources=card();LinearLayout r=row();threadsValue=metric(r,"threads","--");ramValue=metric(r,"ram","-- MiB");resources.addView(r);gap(resources,9);resourceDetail=text("",10,MUTED,false);resourceDetail.setLineSpacing(dp(2),1);resources.addView(resourceDetail);gap(resources,13);r=row();cpuValue=metric(r,"cpu","--");gpuValue=metric(r,"gpu_temperature","--");batteryValue=metric(r,"battery","--");resources.addView(r);gap(resources,8);temperatureDetail=text("",9,MUTED,false);temperatureDetail.setLineSpacing(dp(2),1);resources.addView(temperatureDetail);
        LinearLayout shares=card();r=row();acceptedValue=metric(r,"accepted","0");rejectedValue=metric(r,"rejected","0");sessionValue=metric(r,"session","00:00");shares.addView(r);
        balances();
        LinearLayout p=card();r=row();weight(r,text(t("pool"),12,MUTED,true));poolState=text(t("offline"),11,MUTED,false);r.addView(poolState);p.addView(r);gap(p,8);TextView poolHost=text(prefs().getString("pool","pearl-cpu-eu1.luckypool.io:3370"),12,INK,false);poolHost.setSingleLine();poolHost.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);p.addView(poolHost);
        TextView note=text(t("localnote"),11,MUTED,false);note.setLineSpacing(dp(3),1);note.setPadding(dp(4),0,dp(4),dp(8));body.addView(note);
    }
    private TextView money(LinearLayout r,String label){
        LinearLayout c=column();c.addView(text(t(label),10,MUTED,false));gap(c,5);
        TextView v=text("--",16,INK,true);v.setSingleLine();v.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        add(c,v,-1,23);weight(r,c);return v;
    }
    private void balances(){
        LinearLayout c=card(),r=row();weight(r,text(t("pool_funds")+" · PRL",12,MUTED,true));
        add(r,button(t("refresh"),false,()->{PoolStats.request(prefs().getString("pool",""),prefs().getString("wallet",""),prefs().getString("dataProxy",""),true);refresh();}),80,32);c.addView(r);gap(c,8);
        fundsTotal=text("-- PRL",24,ACCENT,true);fundsTotal.setSingleLine();fundsTotal.setAutoSizeTextTypeUniformWithConfiguration(13,24,1,android.util.TypedValue.COMPLEX_UNIT_SP);add(c,fundsTotal,-1,32);gap(c,10);
        r=row();fundsUnlocked=money(r,"funds_unlocked");add(r,new Space(this),12,1);fundsLocked=money(r,"funds_locked");c.addView(r);gap(c,10);
        r=row();fundsReward=money(r,"funds_24h");add(r,new Space(this),12,1);fundsPaid=money(r,"funds_paid");c.addView(r);gap(c,10);
        fundsMeta=text("",10,MUTED,false);fundsMeta.setLineSpacing(dp(2),1);c.addView(fundsMeta);gap(c,5);
        poolOverview=text("",10,MUTED,false);poolOverview.setLineSpacing(dp(2),1);c.addView(poolOverview);gap(c,5);
        fundsStatus=text("",10,MUTED,false);fundsStatus.setLineSpacing(dp(2),1);c.addView(fundsStatus);gap(c,6);
        String address=prefs().getString("wallet","");
        PoolProvider provider=PoolProvider.from(prefs().getString("pool",""));
        TextView link=text((provider==PoolProvider.CUSTOM?t("custom_pool"):provider.title)+"  ·  "+(address.length()>20?address.substring(0,11)+"…"+address.substring(address.length()-8):"--")+"  ↗",10,ACCENT,true);
        link.setPadding(0,dp(5),0,dp(5));link.setOnClickListener(v->{if(MiningConfig.address(address)&&provider!=PoolProvider.CUSTOM)try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(provider.accountUrl+address)));}catch(ActivityNotFoundException ignored){}});c.addView(link);
    }
    private void refreshBalances(){
        if(fundsTotal==null)return;
        PoolStats.request(prefs().getString("pool",""),prefs().getString("wallet",""),prefs().getString("dataProxy",""),false);
        PoolStats.Snapshot s=PoolStats.snapshot();PoolAccount a=s.account;
        fundsTotal.setText(PoolAccount.amount(a==null?null:a.total())+" PRL");
        fundsUnlocked.setText(PoolAccount.amount(a==null?null:a.unlocked));fundsLocked.setText(PoolAccount.amount(a==null?null:a.locked));
        fundsReward.setText(PoolAccount.amount(a==null?null:a.reward24h));fundsPaid.setText(PoolAccount.amount(a==null?null:a.paid));
        fundsMeta.setText(t("funds_threshold")+" "+PoolAccount.amount(a==null?(s.overview==null?null:s.overview.threshold):a.threshold)+" PRL  ·  "+t("pool_rate")+" "+(a!=null&&Double.isFinite(a.hashrate)?String.format(Locale.US,"%.2f GH/s",a.hashrate/1e9):"--"));
        PoolOverview o=s.overview;
        poolOverview.setText(t("pool_total")+" "+formatRate(o==null?Double.NaN:o.hashrate)+"  ·  "+t("pool_fee")+" "+PoolAccount.amount(o==null?null:o.fee)+"%\n"+t("pool_accounts")+" "+(o==null||o.miners<0?"--":o.miners)+"  ·  "+t("pool_workers")+" "+(o==null||o.workers<0?"--":o.workers));
        boolean stale=a!=null&&SystemClock.elapsedRealtime()-s.elapsed>90000;
        String status=t(s.loading?"pool_loading":a==null&&"pool_error".equals(s.state)?"pool_unavailable":stale&&"pool_fresh".equals(s.state)?"pool_stale":s.state);
        if(s.updated>0)status+=" · "+t("updated")+" "+new java.text.SimpleDateFormat("MM-dd HH:mm:ss",Locale.getDefault()).format(new Date(s.updated));
        fundsStatus.setText(status+"\n"+t("pool_refresh_note"));
        fundsStatus.setTextColor("pool_error".equals(s.state)||stale?ACCENT:MUTED);
    }
    private String formatRate(double value){if(!Double.isFinite(value))return "--";String[] units={"H/s","kH/s","MH/s","GH/s","TH/s","PH/s","EH/s"};int i=0;while(value>=1000&&i<units.length-1){value/=1000;i++;}return String.format(Locale.US,"%.2f %s",value,units[i]);}
    static String defaultWorker(){String name=Build.MODEL.replaceAll("[^A-Za-z0-9_-]","_");return name.isEmpty()?"Android":name.substring(0,Math.min(32,name.length()));}
    private void core(){
        LinearLayout c=card();heading(c,t("engine"));c.addView(text("NoisyGEMM",26,INK,true));gap(c,10);TextView desc=text(t("engineinfo"),13,MUTED,false);desc.setLineSpacing(dp(5),1);c.addView(desc);gap(c,15);
        proofValue=text(t(State.verified?"verified":"unverified"),12,State.verified?GREEN:MUTED,true);c.addView(proofValue);gap(c,12);c.addView(button(t("selftest"),false,()->start("check")),new LinearLayout.LayoutParams(-1,dp(44)));
        c=card();heading(c,t("lastbench"));lastBench=text("-- GH/s",30,INK,true);c.addView(lastBench);gap(c,7);c.addView(text(t("unitnote"),11,MUTED,false));gap(c,14);c.addView(button(t("bench"),true,()->start("bench")),new LinearLayout.LayoutParams(-1,dp(46)));
        c=card();heading(c,t("gpu"));TextView note=text(t("gpuinfo"),12,MUTED,false);note.setLineSpacing(dp(4),1);c.addView(note);
        TextView info=text(t("proofnote"),11,MUTED,false);info.setPadding(dp(4),0,dp(4),dp(10));body.addView(info);
    }
    private EditText input(LinearLayout c,String label,String value,String hint){c.addView(text(t(label),11,MUTED,true));gap(c,6);EditText e=new EditText(this);e.setTextSize(13);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setSingleLine();e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setBackground(border(FIELD,10));e.setPadding(dp(11),dp(8),dp(11),dp(8));e.setText(value);e.setHint(hint);add(c,e,-1,43);gap(c,10);return e;}
    private Spinner picker(LinearLayout c,String label,String[] items,int selected){c.addView(text(t(label),11,MUTED,true));gap(c,5);Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,items){
        @Override public View getView(int p,View v,android.view.ViewGroup group){TextView t=(TextView)super.getView(p,v,group);t.setTextColor(INK);t.setTextSize(13);t.setSingleLine(false);return t;}
        @Override public View getDropDownView(int p,View v,android.view.ViewGroup group){TextView t=(TextView)super.getDropDownView(p,v,group);t.setTextColor(INK);t.setTextSize(14);t.setBackgroundColor(PANEL);t.setPadding(dp(14),dp(12),dp(14),dp(12));return t;}
        };a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(Math.max(0,Math.min(items.length-1,selected)));s.setBackground(border(FIELD,10));add(c,s,-1,42);gap(c,10);return s;}
    private int index(int[] values,int n){for(int i=0;i<values.length;i++)if(values[i]==n)return i;return 0;}
    private void settings(){
        LinearLayout c=card();heading(c,t("pool"));
        c.addView(button(t("pool_presets"),false,()->{
            String[] labels=Arrays.copyOf(PoolProvider.LABELS,PoolProvider.LABELS.length+1);labels[labels.length-1]=t("custom_pool");
            for(int i=0;i<PoolProvider.ENDPOINTS.length;i++)if(PoolProvider.from(PoolProvider.ENDPOINTS[i])==PoolProvider.PEARLHASH)labels[i]+=" · "+t("data_only");
            new AlertDialog.Builder(this).setTitle(t("pool_presets")).setSingleChoiceItems(labels,PoolProvider.preset(pool.getText().toString()),(d,w)->{if(w<PoolProvider.ENDPOINTS.length)pool.setText(PoolProvider.ENDPOINTS[w]);d.dismiss();}).show();
        }),new LinearLayout.LayoutParams(-1,dp(40)));gap(c,10);
        pool=input(c,"pool",prefs().getString("pool","pearl-cpu-eu1.luckypool.io:3370"),"host:port");
        TextView poolHint=text("",10,MUTED,false);c.addView(poolHint);gap(c,10);
        Runnable hint=()->{PoolProvider p=PoolProvider.from(pool.getText().toString());poolHint.setText(t(p==PoolProvider.KRYPTEX?"kryptex_difficulty":p==PoolProvider.PEARLHASH?"pool_protocol":"pool_presets_note"));};hint.run();
        pool.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){hint.run();}public void afterTextChanged(android.text.Editable s){}});
        wallet=input(c,"wallet",prefs().getString("wallet",""),"prl1p…");worker=input(c,"worker",prefs().getString("worker",defaultWorker()),defaultWorker());
        dataProxy=input(c,"data_proxy",prefs().getString("dataProxy",""),t("proxy_hint"));c.addView(text(t("proxy_note"),10,MUTED,false));
        c=card();heading(c,t("resources"));LinearLayout r=row();LinearLayout left=column(),right=column();weight(r,left);add(r,new Space(this),10,1);weight(r,right);c.addView(r);
        int n=Math.min(8,Math.max(1,Runtime.getRuntime().availableProcessors()));String[] ts=new String[n];for(int i=0;i<n;i++)ts[i]=String.valueOf(i+1);threads=picker(left,"threads",ts,prefs().getInt("threads",4)-1);
        String[] shapes=new String[MiningConfig.MATRICES.length];for(int i=0;i<shapes.length;i++)shapes[i]=MiningConfig.matrixLabel(MiningConfig.MATRICES[i]);
        size=picker(right,"matrix",shapes,Arrays.asList(MiningConfig.MATRICES).indexOf(matrixPreference()));c.addView(text(t("memory_note"),11,MUTED,false));
        autoCpu=new Switch(this);autoCpu.setText(t("cpu_auto"));autoCpu.setTextColor(INK);autoCpu.setTextSize(12);autoCpu.setChecked(prefs().getBoolean("autoCpu",false));add(c,autoCpu,-1,44);
        gpu=new Switch(this);gpu.setText(t("gpu_toggle"));gpu.setTextColor(INK);gpu.setTextSize(12);gpu.setChecked(prefs().getBoolean("gpu",false));add(c,gpu,-1,44);
        TextView gpuHint=text(t("gpu_hint"),10,MUTED,false);gpuHint.setLineSpacing(dp(2),1);c.addView(gpuHint);
        c=card();heading(c,t("thermal"));r=row();left=column();right=column();weight(r,left);add(r,new Space(this),10,1);weight(r,right);c.addView(r);
        cpuLimit=picker(left,"cpulimit",new String[]{"70°C","75°C","80°C","85°C","90°C","92°C"},index(new int[]{70,75,80,85,90,92},prefs().getInt("cpuLimit",92)));
        batLimit=picker(right,"batlimit",new String[]{"38°C","40°C","42°C","43°C","45°C"},index(new int[]{38,40,42,43,45},prefs().getInt("batLimit",43)));
        c.addView(text(t("thermal_resume_note"),10,MUTED,false));gap(c,6);
        charge=new Switch(this);charge.setText(t("chargeonly"));charge.setTextSize(12);charge.setTextColor(INK);charge.setChecked(prefs().getBoolean("chargeOnly",true));add(c,charge,-1,42);
        c.addView(button(t("save"),true,this::save),new LinearLayout.LayoutParams(-1,dp(44)));
        c=card();heading(c,t("appearance"));r=row();weight(r,button(t("dark"),!light,()->rebuild(()->prefs().edit().putBoolean("light",false).apply())));add(r,new Space(this),8,1);weight(r,button(t("light"),light,()->rebuild(()->prefs().edit().putBoolean("light",true).apply())));c.addView(r);gap(c,10);
        c.addView(button(t("language")+" · "+Words.LANGUAGES[Math.max(0,Math.min(3,prefs().getInt("language",0)))],false,()->new AlertDialog.Builder(this).setTitle(t("language")).setSingleChoiceItems(Words.LANGUAGES,prefs().getInt("language",0),(d,w)->{d.dismiss();rebuild(()->prefs().edit().putInt("language",w).apply());}).show()),new LinearLayout.LayoutParams(-1,dp(44)));
        gap(c,8);c.addView(button(t("miniw"),false,this::enterMini),new LinearLayout.LayoutParams(-1,dp(42)));
        gap(c,8);c.addView(button(t("licenses"),false,this::licenses),new LinearLayout.LayoutParams(-1,dp(42)));gap(c,10);c.addView(text(t("about"),11,MUTED,false));
    }
    private void save(){
        if(State.active){toast("stopfirst");return;}
        try{
            String p=MiningConfig.pool(pool.getText().toString());String w=wallet.getText().toString().trim();String name=worker.getText().toString().trim();String proxy=MiningConfig.dataProxy(dataProxy.getText().toString());
            if((!w.isEmpty()&&!MiningConfig.address(w))||!MiningConfig.worker(name))throw new IllegalArgumentException();
            prefs().edit().putString("pool",p).putString("wallet",w.toLowerCase(Locale.ROOT)).putString("worker",name).putString("dataProxy",proxy)
                .putInt("threads",threads.getSelectedItemPosition()+1).putString("matrix",MiningConfig.MATRICES[size.getSelectedItemPosition()]).putBoolean("gpu",gpu.isChecked()).putBoolean("autoCpu",autoCpu.isChecked())
                .putInt("cpuLimit",new int[]{70,75,80,85,90,92}[cpuLimit.getSelectedItemPosition()]).putInt("batLimit",new int[]{38,40,42,43,45}[batLimit.getSelectedItemPosition()]).putBoolean("chargeOnly",charge.isChecked()).apply();State.verified=MiningService.verificationKey(matrixPreference()).equals(prefs().getString("verifiedCore",""));toast("saved");
        }catch(Exception e){toast("invalid");}
    }
    private void rebuild(Runnable change){
        String[] draft=page==3&&pool!=null?new String[]{pool.getText().toString(),wallet.getText().toString(),worker.getText().toString(),dataProxy.getText().toString()}:null;
        int[] selected=draft==null?null:new int[]{threads.getSelectedItemPosition(),size.getSelectedItemPosition(),cpuLimit.getSelectedItemPosition(),batLimit.getSelectedItemPosition()};boolean plugged=draft!=null&&charge.isChecked(),gpuDraft=draft!=null&&gpu.isChecked(),autoDraft=draft!=null&&autoCpu.isChecked();int y=scroll==null?0:scroll.getScrollY();
        if(change!=null)change.run();build();
        if(draft!=null){pool.setText(draft[0]);wallet.setText(draft[1]);worker.setText(draft[2]);dataProxy.setText(draft[3]);threads.setSelection(selected[0]);size.setSelection(selected[1]);cpuLimit.setSelection(selected[2]);batLimit.setSelection(selected[3]);charge.setChecked(plugged);gpu.setChecked(gpuDraft);autoCpu.setChecked(autoDraft);scroll.post(()->scroll.scrollTo(0,y));}
    }
    private void logs(){
        LinearLayout tools=row();weight(tools,button(t(errorOnly?"errors":"all"),false,()->{errorOnly=!errorOnly;build();}));add(tools,new Space(this),6,1);weight(tools,button(t("copy"),false,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Pearl logs",State.logText(errorOnly)));}));add(tools,new Space(this),6,1);weight(tools,button(t("clear"),false,()->{State.clearLog();refresh();}));body.addView(tools);gap(body,8);
        Switch auto=new Switch(this);auto.setText(t("follow"));auto.setTextColor(MUTED);auto.setTextSize(12);auto.setChecked(follow);auto.setOnCheckedChangeListener((b,on)->follow=on);add(body,auto,-1,38);
        logScroll=new ScrollView(this);logScroll.setBackground(border(FIELD,14));logView=text("",11,INK,false);logView.setTypeface(Typeface.MONOSPACE);logView.setLineSpacing(dp(4),1);logView.setTextIsSelectable(true);logView.setPadding(dp(11),dp(12),dp(11),dp(12));logScroll.addView(logView);body.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));lastLog="";
    }
    private void start(String mode){
        if(State.active){toast("stopfirst");return;}
        if("mine".equals(mode)&&!MiningConfig.address(prefs().getString("wallet",""))){toast("needwallet");page=3;build();return;}
        if("mine".equals(mode)&&PoolProvider.from(prefs().getString("pool",""))==PoolProvider.PEARLHASH){new AlertDialog.Builder(this).setMessage(t("pool_protocol")).setPositiveButton(android.R.string.ok,null).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        startForegroundService(new Intent(this,MiningService.class).putExtra("mode",mode));handler.postDelayed(this::refresh,150);
    }
    private void stop(){startService(new Intent(this,MiningService.class).setAction(MiningService.STOP));}
    private void toast(String key){Toast.makeText(this,t(key),Toast.LENGTH_SHORT).show();}
    private String temp(float v){return Float.isFinite(v)?String.format(Locale.US,"%.1f°C",v):"--";}
    private void refresh(){
        refreshBalances();
        CpuTemperature.request(this);CpuTemperature.Sample s=CpuTemperature.snapshot();long sensorNow=SystemClock.elapsedRealtime();State.cpu=s.fresh(sensorNow)?s.maximum():Float.NaN;State.gpuTemperature=s.gpuFresh(sensorNow)?s.gpuMaximum():Float.NaN;
        Intent b=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(b!=null)State.battery=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,0)/10f;
        if(rate!=null){rate.setText(String.format(Locale.US,"%.2f",State.rate/1e9));average.setText(String.format(Locale.US,"%.2f GH/s",State.average/1e9));status.setText(t(State.status));threadsValue.setText(State.computing?(State.autoCpu?State.threads+" / "+State.threadLimit:String.valueOf(State.threads)):"0 / "+prefs().getInt("threads",4));ramValue.setText(State.rssMiB+" MiB");cpuValue.setText(("sensor_ap".equals(s.cpuSource)&&Float.isFinite(State.cpu)?"≈":"")+temp(State.cpu));gpuValue.setText(temp(State.gpuTemperature));batteryValue.setText(temp(State.battery));acceptedValue.setText(String.valueOf(State.accepted));rejectedValue.setText(String.valueOf(State.rejected));long seconds=State.active?(SystemClock.elapsedRealtime()-State.started)/1000:State.elapsed/1000;sessionValue.setText(String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60));poolState.setText(t(State.connected?"online":"offline"));chart.invalidate();}
        if(resourceDetail!=null){
            ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mem);
            resourceDetail.setText(MiningConfig.matrixLabel(State.active?State.matrix:matrixPreference())+"  ·  "+t("available_ram")+" "+String.format(Locale.US,"%.1f GiB",mem.availMem/1073741824.0));
            backendDetail.setText(String.format(Locale.US,"CPU %.2f  ·  GPU %.2f GH/s",State.cpuRate/1e9,State.gpuRate/1e9)+"\n"+t(State.gpuStatus));
        }
        if(temperatureDetail!=null){
            temperatureDetail.setText("CPU · "+t(s.cpuLabel(sensorNow))+"  /  GPU · "+t(s.gpuLabel(sensorNow))+"\n"+t("cpu_protect")+" "+prefs().getInt("cpuLimit",92)+"°C  ·  GPU "+GpuPolicy.GPU_LIMIT+"°C");
        }
        if(thermalNotice!=null){
            boolean paused=State.active&&(State.gpuStatus.startsWith("gpu_pause_")||"cooling".equals(State.status));
            thermalNotice.setTextColor(paused?(light?0xff9b4d00:0xffffc077):MUTED);
            thermalNotice.setBackground(shape(paused?(light?0xffffefdc:0xff34271b):FIELD,9));
            thermalNotice.setText(paused?t("cooling".equals(State.status)?"thermal_paused":State.gpuStatus)+"\n"+t("resume_when_ready"):t(State.gpuStatus)+"\n"+t("cpu_protect")+" "+prefs().getInt("cpuLimit",92)+"°C  ·  GPU "+GpuPolicy.GPU_LIMIT+"°C");
        }
        if(proofValue!=null){proofValue.setText(t(State.verified?"verified":"unverified")+" · "+State.simd);proofValue.setTextColor(State.verified?GREEN:MUTED);lastBench.setText(State.lastBenchmark>0?String.format(Locale.US,"%.2f GH/s",State.lastBenchmark/1e9):"-- GH/s");}
        if(logView!=null){String logs=State.logText(errorOnly);if(logs.isEmpty())logs=t("emptylog");if(!logs.equals(lastLog)){lastLog=logs;logView.setText(logs);if(follow)logScroll.post(()->logScroll.fullScroll(View.FOCUS_DOWN));}}
        if(action!=null)action.setText(t(State.active?"stop":"start"));if(mini!=null)mini.invalidate();
        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)&&Build.VERSION.SDK_INT>=31)setPictureInPictureParams(miniParams());
    }
    private PictureInPictureParams miniParams(){PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(2,1));if(Build.VERSION.SDK_INT>=31)b.setAutoEnterEnabled(State.active);PendingIntent p=PendingIntent.getService(this,1,new Intent(this,MiningService.class).setAction(MiningService.STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);b.setActions(Collections.singletonList(new RemoteAction(android.graphics.drawable.Icon.createWithResource(this,android.R.drawable.ic_media_pause),t("stop"),t("stop"),p)));return b.build();}
    private void enterMini(){if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))try{enterPictureInPictureMode(miniParams());}catch(IllegalStateException ignored){}}
    @Override protected void onUserLeaveHint(){super.onUserLeaveHint();if(State.active&&Build.VERSION.SDK_INT<31)enterMini();}
    @Override public void onPictureInPictureModeChanged(boolean small,Configuration config){super.onPictureInPictureModeChanged(small,config);root.setVisibility(small?View.GONE:View.VISIBLE);mini.setVisibility(small?View.VISIBLE:View.GONE);handler.removeCallbacks(tick);tick.run();}
    private void licenses(){try{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=getAssets().open("LICENSES.txt")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}ScrollView scroll=new ScrollView(this);TextView v=text(out.toString("UTF-8"),11,INK,false);v.setPadding(dp(16),dp(12),dp(16),dp(12));scroll.addView(v);new AlertDialog.Builder(this).setTitle(t("licenses")).setView(scroll).setPositiveButton(android.R.string.ok,null).show();}catch(Exception ignored){}}
    private final class Logo extends View {
        private final Paint p=new Paint(3);Logo(){super(MainActivity.this);setContentDescription("Gozero Pearl");}
        @Override protected void onDraw(Canvas c){float r=getWidth()*.4f,x=getWidth()/2f,y=getHeight()/2f;p.setShader(new RadialGradient(x-r*.35f,y-r*.4f,r*1.8f,new int[]{0xffffffff,0xffe7dafa,0xffa58acf,0xff493660},new float[]{0,.35f,.7f,1},Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x449e7cd5);c.drawCircle(x,y,r+dp(3),p);p.setStyle(Paint.Style.FILL);}
    }
    private final class Sparkline extends View {
        private final Paint p=new Paint(3);Sparkline(){super(MainActivity.this);setContentDescription(t("live"));}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight()-dp(8);p.setColor(LINE);p.setStrokeWidth(dp(.5f));for(int i=1;i<=3;i++)c.drawLine(0,h*i/3,w,h*i/3,p);float[] a=State.chart();if(a.length<2)return;float max=1;for(float v:a)max=Math.max(max,v);Path line=new Path();for(int i=0;i<a.length;i++){float x=w*i/(a.length-1),y=h-dp(5)-(h-dp(13))*a[i]/max;if(i==0)line.moveTo(x,y);else line.lineTo(x,y);}Path area=new Path(line);area.lineTo(w,h);area.lineTo(0,h);area.close();p.setShader(new LinearGradient(0,0,0,h,0x449f75ed,0x009f75ed,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawPath(area,p);p.setShader(null);p.setColor(ACCENT);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);}
    }
    private final class Mini extends View {
        private final Paint p=new Paint(3);Mini(){super(MainActivity.this);}
        @Override protected void onDraw(Canvas c){c.drawColor(BG);float w=getWidth();p.setColor(ACCENT);p.setTextSize(w*.06f);c.drawText("GOZERO · PEARL",w*.06f,getHeight()*.23f,p);p.setColor(INK);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(w*.13f);c.drawText(String.format(Locale.US,"%.2f GH/s",State.rate/1e9),w*.06f,getHeight()*.61f,p);p.setColor(MUTED);p.setTextSize(w*.05f);c.drawText(t(State.status)+"  ·  "+State.threads+" CPU",w*.06f,getHeight()*.84f,p);}
    }
}
