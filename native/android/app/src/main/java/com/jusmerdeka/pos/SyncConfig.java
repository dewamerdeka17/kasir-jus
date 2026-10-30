package com.jusmerdeka.pos;
import org.json.JSONObject;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
/** Narrowly scoped remote writer; no credentials are bundled into the APK or backup. */
public final class SyncConfig {
 public static JSONObject validate(String raw) throws Exception {
  if(raw.length()>16000)throw new IllegalArgumentException("File koneksi terlalu besar.");
  JSONObject c=new JSONObject(raw);if(!"jusmerdeka-connection".equals(c.optString("format"))||c.optInt("version")!=1)throw new IllegalArgumentException("Bukan file koneksi JusMerdeka.");
  URI u=new URI(c.getString("origin"));String host=u.getHost();
  if(!"https".equals(u.getScheme())||host==null||!host.endsWith(".chatgpt.site")||u.getUserInfo()!=null||u.getPort()!=-1||u.getRawQuery()!=null||u.getRawFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("Alamat dashboard tidak valid.");
  if(!c.getString("deviceId").matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")||!c.getString("deviceKey").matches("[a-f0-9-]{72}")||!c.getString("serviceAccess").matches("[A-Za-z0-9_-]{32,512}"))throw new IllegalArgumentException("Kunci koneksi tidak valid.");
  SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);f.setLenient(false);f.setTimeZone(TimeZone.getTimeZone("UTC"));
  if(f.parse(c.getString("expiresAt")).getTime()<System.currentTimeMillis())throw new IllegalArgumentException("File koneksi kedaluwarsa. Unduh lagi dari dashboard.");
  c.put("origin","https://"+host);return c;
 }
}
