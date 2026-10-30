package com.jusmerdeka.pos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Only bundled, trusted assets have access to the native SQLite/Bluetooth bridge. */
@SuppressLint("MissingPermission") // Every Bluetooth entry point checks CONNECT before querying or using paired devices.
public class MainActivity extends Activity {
    private static final String HOME="file:///android_asset/pos/index.html";
    private static final int EXPORT=41, IMPORT=42, BLUETOOTH=43, REPORT=44, CONNECT_SYNC=45;
    private static final int MAX_BACKUP_BYTES=25*1024*1024;
    private PosDatabase db;
    private RemoteSync remoteSync;
    private PrinterSelection printerSelection;
    private OfflineSession session;
    private volatile JSONObject pendingReport;
    private WebView web;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean printing=new AtomicBoolean(false);
    private volatile BluetoothSocket socket;
    private volatile boolean destroyed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); db=new PosDatabase(this); session=new OfflineSession(this); remoteSync=new RemoteSync(this,db); printerSelection=new PrinterSelection(getPreferences(MODE_PRIVATE));
        web=new WebView(this); setContentView(web);
        web.setWebChromeClient(new WebChromeClient());
        web.setOnApplyWindowInsetsListener((view,insets)-> {
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false); s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"NativePOS");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r) { return !HOME.equals(r.getUrl().toString()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r) {
                String u=r.getUrl().toString();
                if(u.equals(HOME)||u.equals("file:///android_asset/pos/app.js")||u.equals("file:///android_asset/pos/style.css")) return null;
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        web.loadUrl(HOME);
    }
    private void message(String text,boolean reload) {
        runOnUiThread(()-> {
            if(destroyed) return;
            Toast.makeText(this,text,Toast.LENGTH_LONG).show();
            web.evaluateJavascript("window.nativeResult && window.nativeResult("+JSONObject.quote(text)+","+reload+")",null);
        });
    }
    private boolean bluetoothPermission() { return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED; }
    private BluetoothAdapter adapter() { BluetoothManager m=getSystemService(BluetoothManager.class); return m==null?null:m.getAdapter(); }
    private JSONArray printers() throws Exception {
        if(!bluetoothPermission()) throw new IllegalArgumentException("Izinkan perangkat terdekat lewat tombol Pilih printer.");
        BluetoothAdapter a=adapter();
        if(a==null) throw new IllegalArgumentException("HP tidak mendukung Bluetooth.");
        if(!a.isEnabled()) throw new IllegalArgumentException("Aktifkan Bluetooth di pengaturan HP.");
        JSONArray result=new JSONArray();
        for(BluetoothDevice d:a.getBondedDevices()) result.put(new JSONObject().put("name",d.getName()==null?"Printer Bluetooth":d.getName()).put("address",d.getAddress()));
        return result;
    }
    private JSONObject printerDevices() throws Exception {
        BluetoothAdapter a=adapter();boolean permission=bluetoothPermission();
        JSONObject result=new JSONObject().put("supported",a!=null).put("permission",permission)
            .put("enabled",a!=null&&permission&&a.isEnabled()).put("selected",printerSelection.state(printing.get()));
        JSONArray items=new JSONArray();
        if(a!=null&&permission&&a.isEnabled()){
            java.util.List<JSONObject> sorted=new java.util.ArrayList<>();JSONArray paired=printers();
            for(int i=0;i<paired.length();i++)sorted.add(paired.getJSONObject(i));
            sorted.sort((x,y)->{int c=x.optString("name").compareToIgnoreCase(y.optString("name"));return c==0?x.optString("address").compareTo(y.optString("address")):c;});
            for(JSONObject item:sorted)items.put(item);
        }
        return result.put("items",items);
    }
    private void refreshPrinterUi(){runOnUiThread(()->{if(!destroyed&&web!=null)web.evaluateJavascript("window.refreshPrinterPicker && window.refreshPrinterPicker()",null);});}
    private void choosePrinter() {
        try {session.require("choosePrinter");}catch(IllegalArgumentException e){message(e.getMessage(),false);return;}
        if(!bluetoothPermission()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BLUETOOTH);return;}
        runOnUiThread(()->{if(!destroyed)web.evaluateJavascript("window.openPrinterPicker && window.openPrinterPicker()",null);});
    }
    private void savePrinter(String address)throws Exception{
        if(printing.get())throw new IllegalArgumentException("Tunggu proses cetak selesai sebelum mengganti printer.");
        if(!bluetoothPermission())throw new IllegalArgumentException("Izinkan perangkat terdekat terlebih dahulu.");
        BluetoothAdapter a=adapter();if(a==null||!a.isEnabled())throw new IllegalArgumentException("Aktifkan Bluetooth HP terlebih dahulu.");
        BluetoothDevice d=a.getRemoteDevice(address);if(d.getBondState()!=BluetoothDevice.BOND_BONDED)throw new IllegalArgumentException("Perangkat belum dipasangkan. Buka Bluetooth HP lalu perbarui daftar.");
        printerSelection.select(d.getAddress(),d.getName()==null?"Printer Bluetooth":d.getName());
    }
    private void print(String text){print(text,false);}
    private void print(String text,boolean test) {
        if(!bluetoothPermission()) { message("Izinkan perangkat terdekat melalui Pilih printer.",false); return; }
        String address=getPreferences(MODE_PRIVATE).getString("printer","");
        if(address.isEmpty()) { message("Pilih printer di Pengaturan dahulu. Transaksi tetap tersimpan.",false); return; }
        if(!printing.compareAndSet(false,true)) { message("Masih mencetak struk sebelumnya.",false); return; }
        final String testId=test?printerSelection.beginTest():"";refreshPrinterUi();
        io.execute(()-> {
            String resultMessage;boolean delivered=false;
            BluetoothSocket jobSocket=null; ScheduledFuture<?> timeout=null; final AtomicBoolean expired=new AtomicBoolean(false);
            try {
                BluetoothAdapter a=adapter();
                if(a==null||!a.isEnabled()) throw new IllegalArgumentException("Aktifkan Bluetooth.");
                BluetoothDevice device=a.getRemoteDevice(address);
                if(device.getBondState()!=BluetoothDevice.BOND_BONDED) throw new IllegalArgumentException("Pasangkan ulang printer di pengaturan HP.");
                jobSocket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                socket=jobSocket; final BluetoothSocket pending=jobSocket;
                timeout=timer.schedule(()-> { expired.set(true); try { pending.close(); } catch(Exception ignored) {} },35,TimeUnit.SECONDS);
                jobSocket.connect();
                PrinterTransport.send(jobSocket.getOutputStream(),Receipt.bytes(text),Thread::sleep);
                if(expired.get())throw new java.io.IOException("Printer timeout");
                delivered=true;resultMessage=test?"Data tes terkirim. Periksa kertas, lalu konfirmasi hasilnya.":"Data dikirim ke printer. Periksa hasil cetak sebelum mencetak ulang.";
            } catch(Exception e) { resultMessage="Cetak gagal / hasil belum pasti. Periksa printer sebelum mencoba lagi."; }
            finally {
                if(timeout!=null) timeout.cancel(false);
                if(jobSocket!=null) try { jobSocket.close(); } catch(Exception ignored) {}
                socket=null; printing.set(false);
            }
            if(test)printerSelection.finishTest(address,testId,delivered);
            message(resultMessage,false);refreshPrinterUi();
        });
    }
    public final class Bridge {
        @JavascriptInterface public String request(String raw) {
            synchronized(session) {
            try {
                if(raw.length()>200000) throw new IllegalArgumentException("Permintaan terlalu besar.");
                JSONObject p=new JSONObject(raw); Object result;
                String action=p.getString("action");
                if(!action.equals("session")&&!action.equals("login")&&!action.equals("logout")) session.require(action);
                switch(action) {
                    case "session": result=new JSONObject().put("role",session.role()); break;
                    case "login": result=new JSONObject().put("role",session.login(p.getString("role"),p.getString("pin"))); break;
                    case "logout": session.logout(); result=true; break;
                    case "state": result=new JSONObject().put("products",db.products()).put("settings",db.settings())
                        .put("role",session.role()).put("printerName",getPreferences(MODE_PRIVATE).getString("printerName","Belum dipilih"));
                        if(!session.isAdmin()) {
                            JSONArray products=((JSONObject)result).getJSONArray("products");
                            for(int i=0;i<products.length();i++) products.getJSONObject(i).remove("cost");
                        }
                        break;
                    case "syncStatus": result=remoteSync.status(); break;
                    case "syncNow": remoteSync.trigger(); result=true; break;
                    case "disconnectSync": remoteSync.disconnect(); result=true; break;
                    case "connectSync": runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),CONNECT_SYNC)); result=true; break;
                    case "saveProduct": result=db.saveProduct(p.getJSONObject("product")); break;
                    case "archiveProduct": db.archiveProduct(PosDatabase.number(p,"id",1,Long.MAX_VALUE)); result=true; break;
                    case "checkout": result=publicSale(db.checkoutCash(p)); break;
                    case "history": result=db.history(); break;
                    case "expenses": result=db.expenses(p.getString("date"),session.role()); break;
                    case "addExpense": result=db.addExpense(p.getJSONObject("expense"),session.role()); break;
                    case "cancelExpense": db.cancelExpense(PosDatabase.number(p,"id",1,Long.MAX_VALUE)); result=true; break;
                    case "sale": result=publicSale(db.sale(PosDatabase.number(p,"id",1,Long.MAX_VALUE))); break;
                    case "cancel": db.cancel(PosDatabase.number(p,"id",1,Long.MAX_VALUE)); result=true; break;
                    case "report": result=db.report(p.getString("day")); break;
                    case "reportRange": {
                        JSONObject report=db.reportRange(p.getString("mode"),p.getString("anchor"));
                        result=new JSONObject().put("period",report.getJSONObject("period")).put("summary",report.getJSONObject("summary")).put("daily",report.getJSONArray("daily")).put("bestSellers",report.getJSONArray("bestSellers"));break;
                    }
                    case "exportReport": {
                        JSONObject period=ReportData.period(p.getString("mode"),p.getString("anchor"));
                        pendingReport=new JSONObject().put("mode",p.getString("mode")).put("anchor",p.getString("anchor"));
                        String filename="JusMerdeka-Laporan-"+period.getString("start")+"_"+period.getString("end")+".xlsx";
                        runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,filename),REPORT));result=true;break;
                    }
                    case "saveSettings": db.saveSettings(p.getJSONObject("settings")); result=true; break;
                    case "previewReceipt": result=Receipt.preview(PosDatabase.receiptSettings(p.getJSONObject("settings")),p.optString("method","CASH")); break;
                    case "printerDevices": result=printerDevices(); break;
                    case "savePrinter": savePrinter(p.getString("address"));result=true;break;
                    case "clearPrinter": if(printing.get())throw new IllegalArgumentException("Tunggu proses cetak selesai.");printerSelection.clear();result=true;break;
                    case "confirmPrinterTest": printerSelection.confirm(p.getString("testId"),p.getBoolean("paper"));result=true;break;
                    case "openBluetoothSettings": runOnUiThread(()->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));result=true;break;
                    case "openBluetoothPermissions": runOnUiThread(()->startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));result=true;break;
                    case "choosePrinter": runOnUiThread(()->choosePrinter()); result=true; break;
                    case "print": {
                        JSONObject sale=db.sale(PosDatabase.number(p,"id",1,Long.MAX_VALUE));
                        int cols=Integer.parseInt(db.settings().optString("paper","32"));
                        String receipt=cols==32?sale.getString("receipt"):sale.optString("receipt_wide","");
                        if(receipt.isEmpty()) receipt=Receipt.format(sale,db.settings(),cols);
                        if(sale.getInt("cancelled")==1) receipt="*** DIBATALKAN ***\n"+receipt;
                        print(receipt); result=true; break;
                    }
                    case "testPrint": print("JusMerdeka\nTES PRINTER BLUETOOTH\n12345678901234567890123456789012\nJika terbaca, konfirmasi di aplikasi.\n",true); result=true; break;
                    case "backup": runOnUiThread(()-> {
                        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
                            .putExtra(Intent.EXTRA_TITLE,"JusMerdeka-backup-"+System.currentTimeMillis()+".json");
                        startActivityForResult(i,EXPORT);
                    }); result=true; break;
                    case "restore": runOnUiThread(()->new AlertDialog.Builder(MainActivity.this).setTitle("Pulihkan backup?")
                        .setMessage("Data produk, stok, dan transaksi di HP ini akan DIGANTI dengan isi backup. Buat backup data sekarang sebelum melanjutkan.")
                        .setNegativeButton("Batal",null).setPositiveButton("Pilih backup",(dialog,w)->startActivityForResult(
                            new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT)).show()); result=true; break;
                    default: throw new IllegalArgumentException("Aksi tidak dikenal.");
                }
                if(java.util.Arrays.asList("checkout","addExpense","cancelExpense","cancel","saveProduct","archiveProduct","saveSettings").contains(action))remoteSync.trigger();
                return new JSONObject().put("ok",true).put("data",result).toString();
            } catch(Exception e) {
                try { return new JSONObject().put("ok",false).put("error",e instanceof IllegalArgumentException?e.getMessage():"Operasi gagal. Data belum diubah; periksa input atau backup.").toString(); }
                catch(Exception ignored) { return "{\"ok\":false,\"error\":\"Operasi gagal\"}"; }
            }
            }
        }
    }
    private JSONObject publicSale(JSONObject sale) throws Exception {
        if(!session.isAdmin()) {
            JSONArray items=sale.optJSONArray("items");
            if(items!=null) for(int i=0;i<items.length();i++) items.getJSONObject(i).remove("cost");
        }
        return sale;
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants) {
        super.onRequestPermissionsResult(code,names,grants);
        if(code==BLUETOOTH) { if(bluetoothPermission()) choosePrinter(); else {message("Izin perangkat terdekat belum aktif. Buka izin aplikasi untuk mengaktifkannya.",false);refreshPrinterUi();} }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null) return;
        Uri uri=data.getData();
        io.execute(()-> {
            try {
                synchronized(session) {
                session.require(request==CONNECT_SYNC?"connectSync":request==EXPORT?"backup":request==REPORT?"exportReport":"restore");
                if(request==CONNECT_SYNC) {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IllegalArgumentException("File koneksi tidak dapat dibaca.");byte[] part=new byte[2048];int n;while((n=in.read(part))!=-1){if(bytes.size()+n>16000)throw new IllegalArgumentException("File koneksi terlalu besar.");bytes.write(part,0,n);}}
                    remoteSync.connect(bytes.toString("UTF-8"));message("Dashboard terhubung. Mengirim data awal…",false);
                } else if(request==REPORT) {
                    JSONObject params=pendingReport;if(params==null)throw new IllegalArgumentException("Pilih periode ekspor kembali.");
                    JSONObject report=db.reportRange(params.getString("mode"),params.getString("anchor"));
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                        if(out==null)throw new IllegalArgumentException("File Excel tidak dapat ditulis.");XlsxReport.write(MainActivity.this,report,out);
                    }
                    pendingReport=null;message("Laporan Excel berhasil disimpan.",false);
                } else if(request==EXPORT) {
                    byte[] bytes=db.backup().getBytes(StandardCharsets.UTF_8);
                    if(bytes.length>MAX_BACKUP_BYTES) throw new IllegalArgumentException("Backup melebihi batas 25 MB. Hubungi pengembang untuk ekspor data besar.");
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                        if(out==null) throw new IllegalArgumentException("File tidak dapat ditulis."); out.write(bytes); out.flush();
                    }
                    message("Backup berhasil disimpan. Simpan salinannya di luar HP ini.",false);
                } else if(request==IMPORT) {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    try(InputStream in=getContentResolver().openInputStream(uri)) {
                        if(in==null) throw new IllegalArgumentException("File tidak dapat dibaca.");
                        byte[] buffer=new byte[8192]; int n;
                        while((n=in.read(buffer))!=-1) { if(bytes.size()+n>MAX_BACKUP_BYTES) throw new IllegalArgumentException("Backup melebihi 25 MB."); bytes.write(buffer,0,n); }
                    }
                    String backup=bytes.toString("UTF-8");
                    // Confirmation follows file selection too, so the chosen snapshot can be reviewed.
                    JSONObject info=new JSONObject(backup);
                    String details="Backup: "+info.optString("exportedAt","tanggal tidak diketahui")+"\n"+info.getJSONArray("products").length()+" produk, "+info.getJSONArray("sales").length()+" transaksi.\n\nGanti seluruh data di HP ini?";
                    runOnUiThread(()-> { if(!destroyed) new AlertDialog.Builder(MainActivity.this).setTitle("Konfirmasi pemulihan")
                        .setMessage(details).setNegativeButton("Batal",null).setPositiveButton("Ganti data",(d,w)->io.execute(()-> {
                            try { synchronized(session) { session.require("restore"); db.restore(backup); remoteSync.trigger(); } message("Backup dipulihkan. Keranjang dikosongkan.",true); }
                            catch(Exception e) { message("Backup tidak valid. Data lama tetap tersimpan.",false); }
                        })).show(); });
                }
                }
            } catch(Exception e) { message(e instanceof IllegalArgumentException?e.getMessage():"Proses file gagal. Data kasir tetap tersimpan.",false); }
        });
    }
    @Override protected void onResume(){super.onResume();refreshPrinterUi();}
    @Override public void onBackPressed() { web.evaluateJavascript("window.goHome && window.goHome()",null); }
    @Override protected void onDestroy() {
        destroyed=true;
        if(remoteSync!=null)remoteSync.close();
        if(socket!=null) try { socket.close(); } catch(Exception ignored) {}
        web.removeJavascriptInterface("NativePOS"); web.destroy(); io.shutdown(); timer.shutdownNow();
        // The helper is process-scoped for pending bridge / document operations; never close mid-checkout.
        super.onDestroy();
    }
}
