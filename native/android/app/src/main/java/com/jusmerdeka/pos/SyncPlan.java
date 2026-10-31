package com.jusmerdeka.pos;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Content-addressed delta plan. Checkpoint is advanced only after the server's atomic commit. */
public final class SyncPlan {
 public final JSONObject hashes=new JSONObject();public final List<JSONArray> batches=new ArrayList<>();public int changed;
 public SyncPlan(JSONObject snapshot,JSONObject previous,boolean reset) throws Exception {
  JSONArray changes=new JSONArray();
  for(String kind:new String[]{"products","sales","expenses"}){JSONArray records=snapshot.getJSONArray(kind);for(int i=0;i<records.length();i++){JSONObject row=records.getJSONObject(i);long id=row.getLong("id");String key=kind+"/"+id,digest=hash(canonical(row));hashes.put(key,digest);if(reset||!digest.equals(previous.optString(key)))changes.put(new JSONObject().put("op","put").put("kind",kind).put("id",id).put("data",row));}}
  if(!reset){Iterator<String> keys=previous.keys();while(keys.hasNext()){String key=keys.next();if(!hashes.has(key)){String[] p=key.split("/");if(p.length==2&&Arrays.asList("products","sales","expenses").contains(p[0]))changes.put(new JSONObject().put("op","remove").put("kind",p[0]).put("id",Long.parseLong(p[1])));}}}
  changed=changes.length();JSONArray batch=new JSONArray();int size=2;
  for(int i=0;i<changes.length();i++){JSONObject c=changes.getJSONObject(i);int bytes=c.toString().getBytes(StandardCharsets.UTF_8).length+1;if(bytes>250000)throw new IllegalArgumentException("Satu catatan terlalu besar untuk dikirim. Hubungi pengembang.");if(batch.length()>0&&(batch.length()>=100||size+bytes>250000)){batches.add(batch);batch=new JSONArray();size=2;}batch.put(c);size+=bytes;}
  if(batch.length()>0)batches.add(batch);if(batches.size()>10000)throw new IllegalArgumentException("Terlalu banyak catatan untuk satu sinkronisasi. Hubungi pengembang.");
 }
 private static String hash(String value) throws Exception {byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:digest)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}
 private static String canonical(Object value) throws Exception {
  if(value instanceof JSONObject){JSONObject obj=(JSONObject)value;List<String> keys=new ArrayList<>();Iterator<String> it=obj.keys();while(it.hasNext())keys.add(it.next());Collections.sort(keys);StringBuilder b=new StringBuilder("{");for(String k:keys){if(b.length()>1)b.append(',');b.append(JSONObject.quote(k)).append(':').append(canonical(obj.get(k)));}return b.append('}').toString();}
  if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder b=new StringBuilder("[");for(int i=0;i<a.length();i++){if(i>0)b.append(',');b.append(canonical(a.get(i)));}return b.append(']').toString();}
  if(value==null||value==JSONObject.NULL)return "null";if(value instanceof String)return JSONObject.quote((String)value);return value.toString();
 }
}
