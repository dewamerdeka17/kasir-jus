package com.jusmerdeka.pos;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
/** One serialized uploader. SQLite remains authoritative; the cloud never mutates local data. */
public final class RemoteSync {
 private static final String ALIAS="jusmerdeka-sync-v1";
 private final SharedPreferences prefs;private final PosDatabase db;
 private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor();
 private volatile boolean busy,closed;private final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();private volatile HttpURLConnection connection;
 private final Object lock=new Object();
 public RemoteSync(Context c,PosDatabase db){this.db=db;prefs=c.getSharedPreferences("remote-sync",Context.MODE_PRIVATE);executor.scheduleWithFixedDelay(()->send(false),5,60,TimeUnit.SECONDS);}
 private SecretKey key() throws Exception {KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
 private JSONObject config() throws Exception {String saved=prefs.getString("config","");if(saved.isEmpty())return null;JSONObject b=new JSONObject(saved);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(b.getString("iv"),Base64.NO_WRAP)));return new JSONObject(new String(cipher.doFinal(Base64.decode(b.getString("data"),Base64.NO_WRAP)),StandardCharsets.UTF_8));}
 public void connect(String raw) throws Exception {JSONObject c=SyncConfig.validate(raw);synchronized(lock){Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());JSONObject b=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(c.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));long revision=c.getString("deviceId").equals(prefs.getString("deviceId",""))?prefs.getLong("revision",0):0;if(!prefs.edit().clear().putLong("revision",revision).putString("config",b.toString()).putString("origin",c.getString("origin")).putString("deviceId",c.getString("deviceId")).putString("status","Siap mengirim data.").commit())throw new IllegalArgumentException("Koneksi belum tersimpan.");}trigger();}
 public void disconnect(){synchronized(lock){prefs.edit().clear().commit();}HttpURLConnection c=connection;if(c!=null)c.disconnect();}
 public JSONObject status() throws Exception {return new JSONObject().put("configured",prefs.contains("config")).put("origin",prefs.getString("origin","")).put("lastSuccess",prefs.getString("lastSuccess","")).put("status",prefs.getString("status","Belum terhubung.")).put("busy",busy).put("pending",prefs.getInt("pending",0));}
 public void trigger(){if(!closed&&queued.compareAndSet(false,true))executor.execute(()->{queued.set(false);send(true);});}
 private void setStatus(String device,String text,String success){synchronized(lock){if(!device.equals(prefs.getString("deviceId","")))return;SharedPreferences.Editor e=prefs.edit().putString("status",text);if(success!=null)e.putString("lastSuccess",success);e.commit();}}
 private void send(boolean manual){
  if(closed||busy||!prefs.contains("config"))return;busy=true;String device="";HttpURLConnection c=null;
  try{
   JSONObject cfg;long revision;synchronized(lock){cfg=config();if(cfg==null)return;device=cfg.getString("deviceId");revision=prefs.getLong("revision",0)+1;if(cfg.optInt("version")==1&&!prefs.edit().putLong("revision",revision).commit())throw new IllegalArgumentException("Nomor sinkronisasi gagal disimpan.");}
   if(cfg.optInt("version")==2){sendDelta(cfg,device);return;}
   JSONObject snapshot=db.monitorSnapshot();byte[] bytes=new JSONObject().put("format","jusmerdeka-monitor").put("version",1).put("deviceId",device).put("revision",revision).put("snapshot",snapshot).toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>12*1024*1024){setStatus(device,"Data melebihi 12 MB. Hubungi pengembang; kasir tetap tersimpan.",null);return;}
   setStatus(device,"Mengirim pembaruan…",null);
   c=(HttpURLConnection)new URL(cfg.getString("origin")+"/api/sync").openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("OAI-Sites-Authorization","Bearer "+cfg.getString("serviceAccess"));c.setRequestProperty("Authorization","Bearer "+cfg.getString("deviceKey"));c.setRequestProperty("X-POS-Device",device);c.setFixedLengthStreamingMode(bytes.length);
   try(java.io.OutputStream out=c.getOutputStream()){out.write(bytes);}
   int code=c.getResponseCode();if(code==401||code==403){setStatus(device,"Koneksi ditolak / dicabut. Unduh file koneksi baru di dashboard.",null);return;}if(code!=200)throw new java.io.IOException();
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] part=new byte[1024];int n;while((n=in.read(part))!=-1){if(buffer.size()+n>16000)throw new java.io.IOException();buffer.write(part,0,n);}}
   JSONObject ack=new JSONObject(buffer.toString("UTF-8"));if(!ack.optBoolean("ok")||ack.optLong("revision")!=revision)throw new java.io.IOException();
   setStatus(device,"Data sudah terkirim.",ack.getString("receivedAt"));
  }catch(Exception e){if(!device.isEmpty())setStatus(device,e instanceof IllegalArgumentException?e.getMessage():"Belum terkirim. Mencoba lagi saat aplikasi terbuka dan internet tersedia.",null);else synchronized(lock){prefs.edit().putString("status","Kunci koneksi tidak dapat dibaca. Hubungkan ulang dari dashboard.").commit();}}
  finally{if(c!=null)c.disconnect();connection=null;busy=false;}
 }
 private JSONObject post(JSONObject cfg,JSONObject payload) throws Exception {
  if(closed)throw new java.io.IOException();
  byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>512*1024)throw new IllegalArgumentException("Batch terlalu besar.");
  HttpURLConnection c=(HttpURLConnection)new URL(cfg.getString("origin")+"/api/sync").openConnection();connection=c;
  try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(60000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Authorization","Bearer "+cfg.getString("deviceKey"));c.setRequestProperty("X-POS-Device",cfg.getString("deviceId"));c.setFixedLengthStreamingMode(bytes.length);
   try(java.io.OutputStream out=c.getOutputStream()){out.write(bytes);}int code=c.getResponseCode();
   if(code==401||code==403)throw new IllegalArgumentException("Koneksi ditolak atau dicabut. Unduh file koneksi baru dari dashboard.");
   if(code!=200)throw new java.io.IOException();ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] part=new byte[1024];int n;while((n=in.read(part))!=-1){if(b.size()+n>16000)throw new java.io.IOException();b.write(part,0,n);}}
   JSONObject result=new JSONObject(b.toString("UTF-8"));if(!result.optBoolean("ok"))throw new java.io.IOException();return result;
  }finally{c.disconnect();connection=null;}
 }
 private void sendDelta(JSONObject cfg,String device) throws Exception {
  JSONObject remote=post(cfg,new JSONObject().put("operation","status")),oldHashes;long base=remote.getLong("revision"),revision;boolean reset;
  synchronized(lock){if(!device.equals(prefs.getString("deviceId","")))return;reset=!prefs.contains("hashes")||prefs.getLong("syncedRevision",-1)!=base;oldHashes=reset?new JSONObject():new JSONObject(prefs.getString("hashes","{}"));revision=Math.max(prefs.getLong("revision",0),base)+1;if(!prefs.edit().putLong("revision",revision).commit())throw new java.io.IOException();}
  JSONObject snapshot=db.monitorSnapshot();SyncPlan plan=new SyncPlan(snapshot,oldHashes,reset);String upload=java.util.UUID.randomUUID().toString();
  synchronized(lock){if(!device.equals(prefs.getString("deviceId","")))return;prefs.edit().putInt("pending",plan.changed).commit();}
  setStatus(device,plan.changed>0?"Mengirim "+plan.changed+" catatan yang berubah…":"Memperbarui waktu sinkronisasi…",null);
  post(cfg,new JSONObject().put("operation","start").put("uploadId",upload).put("revision",revision).put("baseRevision",base).put("reset",reset).put("expectedBatches",plan.batches.size()).put("shopName",snapshot.getJSONObject("shop").getString("name")).put("capturedAt",snapshot.getString("capturedAt")));
  for(int i=0;i<plan.batches.size();i++){synchronized(lock){if(!device.equals(prefs.getString("deviceId","")))return;}post(cfg,new JSONObject().put("operation","batch").put("uploadId",upload).put("index",i).put("changes",plan.batches.get(i)));setStatus(device,"Mengirim batch "+(i+1)+" / "+plan.batches.size()+"…",null);}
  JSONObject ack=post(cfg,new JSONObject().put("operation","commit").put("uploadId",upload));if(ack.getLong("revision")!=revision)throw new java.io.IOException();
  synchronized(lock){if(!device.equals(prefs.getString("deviceId","")))return;if(!prefs.edit().putString("hashes",plan.hashes.toString()).putLong("syncedRevision",revision).putInt("pending",0).putString("lastSuccess",ack.getString("receivedAt")).putString("status","Data tersinkron. Salinan lokal tetap tersimpan.").commit())throw new java.io.IOException();}
 }
 public void close(){closed=true;executor.shutdownNow();if(connection!=null)connection.disconnect();}
}
