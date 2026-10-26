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
    private static final int EXPORT=41, IMPORT=42, BLUETOOTH=43;
    private static final int MAX_BACKUP_BYTES=25*1024*1024;
    private PosDatabase db;
    private OfflineSession session;
    private WebView web;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean printing=new AtomicBoolean(false);
    private volatile BluetoothSocket socket;
    private volatile boolean destroyed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); db=new PosDatabase(this); session=new OfflineSession(this);
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
    private void choosePrinter() {
        try { session.require("choosePrinter"); } catch(IllegalArgumentException e) { message(e.getMessage(),false); return; }
        if(!bluetoothPermission()) { requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},BLUETOOTH); return; }
        try {
            JSONArray devices=printers();
            if(devices.length()==0) {
                new AlertDialog.Builder(this).setTitle("Belum ada perangkat berpasangan")
                    .setMessage("Pasangkan printer Bluetooth Classic/SPP lewat pengaturan HP, lalu pilih kembali di sini.")
                    .setPositiveButton("Buka Bluetooth",(d,w)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
                    .setNegativeButton("Tutup",null).show(); return;
            }
            String[] labels=new String[devices.length()];
            for(int i=0;i<labels.length;i++) { JSONObject d=devices.getJSONObject(i); labels[i]=d.getString("name")+"\n"+d.getString("address"); }
            new AlertDialog.Builder(this).setTitle("Pilih printer yang sudah dipasangkan")
                .setItems(labels,(dialog,index)-> {
                    try {
                        JSONObject d=devices.getJSONObject(index);
                        getPreferences(MODE_PRIVATE).edit().putString("printer",d.getString("address")).putString("printerName",d.getString("name")).apply();
                        message("Printer dipilih. Gunakan Tes cetak untuk memeriksa.",false);
                    } catch(Exception e) { message("Printer gagal dipilih.",false); }
                }).setNegativeButton("Tutup",null).show();
        } catch(Exception e) { message(e.getMessage(),false); }
    }
    private void print(String text) {
        if(!bluetoothPermission()) { message("Izinkan perangkat terdekat melalui Pilih printer.",false); return; }
        String address=getPreferences(MODE_PRIVATE).getString("printer","");
        if(address.isEmpty()) { message("Pilih printer di Pengaturan dahulu. Transaksi tetap tersimpan.",false); return; }
        if(!printing.compareAndSet(false,true)) { message("Masih mencetak struk sebelumnya.",false); return; }
        io.execute(()-> {
            BluetoothSocket jobSocket=null; ScheduledFuture<?> timeout=null;
            try {
                BluetoothAdapter a=adapter();
                if(a==null||!a.isEnabled()) throw new IllegalArgumentException("Aktifkan Bluetooth.");
                BluetoothDevice device=a.getRemoteDevice(address);
                if(device.getBondState()!=BluetoothDevice.BOND_BONDED) throw new IllegalArgumentException("Pasangkan ulang printer di pengaturan HP.");
                jobSocket=device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                socket=jobSocket; final BluetoothSocket pending=jobSocket;
                timeout=timer.schedule(()-> { try { pending.close(); } catch(Exception ignored) {} },15,TimeUnit.SECONDS);
                jobSocket.connect(); OutputStream out=jobSocket.getOutputStream(); out.write(Receipt.bytes(text)); out.flush();
                message("Struk dikirim. Periksa hasil di printer sebelum cetak ulang.",false);
            } catch(Exception e) { message("Cetak gagal / hasil belum pasti. Transaksi tetap tersimpan. Periksa printer sebelum cetak ulang.",false); }
            finally {
                if(timeout!=null) timeout.cancel(false);
                if(jobSocket!=null) try { jobSocket.close(); } catch(Exception ignored) {}
                socket=null; printing.set(false);
            }
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
                    case "saveSettings": db.saveSettings(p.getJSONObject("settings")); result=true; break;
                    case "choosePrinter": runOnUiThread(()->choosePrinter()); result=true; break;
                    case "print": {
                        JSONObject sale=db.sale(PosDatabase.number(p,"id",1,Long.MAX_VALUE));
                        int cols=(int)PosDatabase.number(p,"columns",32,48);
                        String receipt=cols==32&&sale.getInt("cancelled")==0?sale.getString("receipt"):Receipt.format(sale,db.settings(),cols);
                        print(receipt); result=true; break;
                    }
                    case "testPrint": print("JusMerdeka\nTES PRINTER BLUETOOTH\n12345678901234567890123456789012\nJika terbaca, printer siap.\n"); result=true; break;
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
        if(code==BLUETOOTH) { if(bluetoothPermission()) choosePrinter(); else message("Izin Bluetooth ditolak. Kasir tetap dapat digunakan tanpa printer.",false); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null) return;
        Uri uri=data.getData();
        io.execute(()-> {
            try {
                synchronized(session) {
                session.require(request==EXPORT?"backup":"restore");
                if(request==EXPORT) {
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
                            try { synchronized(session) { session.require("restore"); db.restore(backup); } message("Backup dipulihkan. Keranjang dikosongkan.",true); }
                            catch(Exception e) { message("Backup tidak valid. Data lama tetap tersimpan.",false); }
                        })).show(); });
                }
                }
            } catch(Exception e) { message(e instanceof IllegalArgumentException?e.getMessage():"Proses file gagal. Data kasir tetap tersimpan.",false); }
        });
    }
    @Override public void onBackPressed() { web.evaluateJavascript("window.goHome && window.goHome()",null); }
    @Override protected void onDestroy() {
        destroyed=true;
        if(socket!=null) try { socket.close(); } catch(Exception ignored) {}
        web.removeJavascriptInterface("NativePOS"); web.destroy(); io.shutdown(); timer.shutdownNow();
        // The helper is process-scoped for pending bridge / document operations; never close mid-checkout.
        super.onDestroy();
    }
}
