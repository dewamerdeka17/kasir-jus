package com.jusmerdeka.pos;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SyncTest {
 @Test public void monitorSnapshotOmitsPrivateFieldsAndTracksCancellation() throws Exception {
  android.content.Context ctx=RuntimeEnvironment.getApplication();ctx.deleteDatabase("jus-offline.db");PosDatabase db=new PosDatabase(ctx);
  try {long id=db.saveProduct(new JSONObject().put("id",0).put("name","Mangga").put("category","Jus").put("price",12000).put("cost",4000).put("stock",5));
   JSONObject sale=db.checkoutCash(new JSONObject().put("requestId","monitor-sale-00001").put("discount",0).put("paid",12000).put("paymentMethod","CASH").put("items",new JSONArray().put(new JSONObject().put("id",id).put("qty",1))));
   JSONObject a=db.monitorSnapshot();assertEquals(1,a.getJSONArray("sales").length());String raw=a.toString();assertFalse(raw.contains("cost"));assertFalse(raw.contains("receipt"));assertFalse(raw.contains("phone"));assertFalse(raw.contains("request_id"));assertFalse(raw.contains("pin"));
   db.cancel(sale.getLong("id"));assertEquals(1,db.monitorSnapshot().getJSONArray("sales").getJSONObject(0).getInt("cancelled"));
  }finally{db.close();}
 }
 @Test public void connectionOnlyAcceptsHttpsSitesWithoutCredentialRedirects() throws Exception {
  JSONObject c=new JSONObject().put("format","jusmerdeka-connection").put("version",1).put("origin","https://jusmerdeka-monitor.example.chatgpt.site").put("deviceId","12345678-1234-1234-1234-123456789abc").put("deviceKey","12345678-1234-1234-1234-123456789abc12345678-1234-1234-1234-123456789abc").put("serviceAccess","a".repeat(43)).put("expiresAt","2099-12-31T23:59:59.000Z");
  assertEquals(c.getString("origin"),SyncConfig.validate(c.toString()).getString("origin"));
  for(String bad:new String[]{"http://jus.chatgpt.site","https://evil.example","https://good.chatgpt.site@evil.example","https://good.chatgpt.site/api/sync","https://good.chatgpt.site?token=bad","https://good.chatgpt.site:443"}){c.put("origin",bad);try{SyncConfig.validate(c.toString());fail("Invalid URL accepted");}catch(IllegalArgumentException expected){}}
 }
 @Test public void cashierCannotManageCloudConnection() throws Exception {
  OfflineSession s=new OfflineSession(RuntimeEnvironment.getApplication());s.login("CASHIER","123456");
  for(String action:new String[]{"syncStatus","syncNow","connectSync","disconnectSync"})try{s.require(action);fail("Cashier accessed "+action);}catch(IllegalArgumentException expected){}
 }
}
