package com.jusmerdeka.pos;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SyncPlanTest {
 private JSONObject product(long id) throws Exception{return new JSONObject().put("id",id).put("name","Jus Mangga").put("category","Jus").put("price",12000).put("stock",5).put("active",1);}
 private JSONObject snapshot(JSONArray products) throws Exception{return new JSONObject().put("products",products).put("sales",new JSONArray()).put("expenses",new JSONArray());}
 @Test public void onlyChangedRecordsAreSentAndPlanningDoesNotAdvanceCheckpoint() throws Exception{
  JSONObject source=snapshot(new JSONArray().put(product(1)).put(product(2)));JSONObject previous=new JSONObject();SyncPlan initial=new SyncPlan(source,previous,true);assertEquals(2,initial.changed);assertEquals(0,previous.length());assertEquals(0,new SyncPlan(source,initial.hashes,false).changed);
  source.getJSONArray("products").getJSONObject(0).put("stock",4);SyncPlan delta=new SyncPlan(source,initial.hashes,false);assertEquals(1,delta.changed);assertEquals(1,delta.batches.get(0).getJSONObject(0).getLong("id"));assertEquals(1,new SyncPlan(source,initial.hashes,false).changed);
 }
 @Test public void restoreRemovalsBecomeTombstonesAndLostCheckpointResendsFullLedger() throws Exception{
  SyncPlan initial=new SyncPlan(snapshot(new JSONArray().put(product(1)).put(product(2))),new JSONObject(),true);JSONObject restored=snapshot(new JSONArray().put(product(1)));SyncPlan delta=new SyncPlan(restored,initial.hashes,false);assertEquals("remove",delta.batches.get(0).getJSONObject(0).getString("op"));assertEquals(2,delta.batches.get(0).getJSONObject(0).getLong("id"));SyncPlan reset=new SyncPlan(restored,new JSONObject(),true);assertEquals(1,reset.changed);assertEquals("put",reset.batches.get(0).getJSONObject(0).getString("op"));
 }
 @Test public void canonicalHashesIgnoreJsonKeyOrder() throws Exception{
  JSONObject p=product(1),reverse=new JSONObject();for(String key:new String[]{"active","stock","price","category","name","id"})reverse.put(key,p.get(key));SyncPlan first=new SyncPlan(snapshot(new JSONArray().put(p)),new JSONObject(),true);assertEquals(0,new SyncPlan(snapshot(new JSONArray().put(reverse)),first.hashes,false).changed);
 }
 @Test public void batchesAreBoundedAndCancelledSaleIsAnUpdate() throws Exception{
  JSONArray products=new JSONArray();for(int i=1;i<=251;i++)products.put(product(i));SyncPlan plan=new SyncPlan(snapshot(products),new JSONObject(),true);assertEquals(3,plan.batches.size());for(JSONArray batch:plan.batches){assertTrue(batch.length()<=100);assertTrue(batch.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=250000);}
  JSONObject source=snapshot(new JSONArray()).put("sales",new JSONArray().put(new JSONObject().put("id",1).put("cancelled",0)));SyncPlan initial=new SyncPlan(source,new JSONObject(),true);source.getJSONArray("sales").getJSONObject(0).put("cancelled",1);SyncPlan cancelled=new SyncPlan(source,initial.hashes,false);assertEquals(1,cancelled.changed);assertEquals(1,cancelled.batches.get(0).getJSONObject(0).getJSONObject("data").getInt("cancelled"));
 }
}
