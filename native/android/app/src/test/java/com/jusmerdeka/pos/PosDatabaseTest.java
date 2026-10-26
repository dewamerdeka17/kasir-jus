package com.jusmerdeka.pos;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class PosDatabaseTest {
    private PosDatabase db;
    private long id;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().deleteDatabase("jus-offline.db");
        db=new PosDatabase(RuntimeEnvironment.getApplication());
        id=db.saveProduct(new JSONObject().put("id",0).put("name","Jus Mangga").put("category","Jus").put("price",10000).put("cost",4000).put("stock",5));
    }
    @After public void close() { db.close(); }
    private JSONObject order(String key,long qty,long paid) throws Exception {
        return new JSONObject().put("requestId",key).put("discount",1000).put("paid",paid)
            .put("items",new JSONArray().put(new JSONObject().put("id",id).put("qty",qty)));
    }
    private long stock() throws Exception { return db.products().getJSONObject(0).getLong("stock"); }
    @Test public void checkoutIsDurableAndIdempotent() throws Exception {
        JSONObject input=order("checkout-request-0001",2,20000);
        JSONObject s=db.checkout(input);
        assertEquals(19000,s.getLong("total")); assertEquals(3,stock());
        db.close(); db=new PosDatabase(RuntimeEnvironment.getApplication());
        assertEquals(s.getLong("id"),db.checkout(input).getLong("id"));
        assertEquals(1,db.history().length()); assertEquals(3,stock());
    }
    @Test public void insufficientCashAndStockLeaveNoPartialSale() throws Exception {
        try { db.checkout(order("checkout-request-0002",2,100)); fail("Expected insufficient cash"); } catch(IllegalArgumentException expected) {}
        try { db.checkout(order("checkout-request-0003",6,100000)); fail("Expected insufficient stock"); } catch(IllegalArgumentException expected) {}
        assertEquals(0,db.history().length()); assertEquals(5,stock());
    }
    @Test public void duplicateProductLinesAreCombinedAndStockChecked() throws Exception {
        JSONObject p=order("checkout-request-0004",3,100000);
        p.getJSONArray("items").put(new JSONObject().put("id",id).put("qty",3));
        try { db.checkout(p); fail("Expected stock validation after grouping"); } catch(IllegalArgumentException expected) {}
        assertEquals(5,stock()); assertEquals(0,db.history().length());
    }
    @Test public void cancellingTwiceReturnsStockOnlyOnceAndRemovesRevenue() throws Exception {
        JSONObject sale=db.checkout(order("checkout-request-0005",2,20000));
        String day=sale.getString("created_at").substring(0,10);
        assertEquals(11000,db.report(day).getLong("profit"));
        db.cancel(sale.getLong("id")); db.cancel(sale.getLong("id"));
        assertEquals(5,stock()); assertEquals(0,db.report(day).getLong("revenue"));
    }
    @Test public void backupRoundTripPreservesLedgerAndRejectsCorruption() throws Exception {
        JSONObject sale=db.checkout(order("checkout-request-0006",1,10000));
        String backup=db.backup(); db.cancel(sale.getLong("id")); db.restore(backup);
        assertEquals(4,stock()); assertEquals(0,db.sale(sale.getLong("id")).getInt("cancelled"));
        JSONObject invalid=new JSONObject(backup);
        invalid.getJSONArray("sales").getJSONObject(0).put("total",10000000);
        try { db.restore(invalid.toString()); fail("Expected invalid backup rejection"); } catch(IllegalArgumentException expected) {}
        assertEquals(backupLedger(backup),backupLedger(db.backup()));
    }
    @Test public void duplicateInvoicesRollBackTheEntireRestore() throws Exception {
        db.checkout(order("checkout-request-0007",1,10000));
        db.checkout(order("checkout-request-0008",1,10000));
        String before=db.backup(); JSONObject bad=new JSONObject(before);
        JSONArray sales=bad.getJSONArray("sales"); sales.getJSONObject(1).put("invoice",sales.getJSONObject(0).getString("invoice"));
        try { db.restore(bad.toString()); fail("Expected unique constraint rejection"); } catch(android.database.sqlite.SQLiteConstraintException expected) {}
        assertEquals(backupLedger(before),backupLedger(db.backup()));
    }
    @Test public void receiptCannotInjectPrinterCommandsAndFitsPaper() throws Exception {
        JSONObject sale=db.checkout(order("checkout-request-0009",1,10000));
        JSONObject shop=db.settings().put("name","Jus\u001b@Merdeka "+"x".repeat(70));
        for(int columns:new int[]{32,48}) {
            String receipt=Receipt.format(sale,shop,columns);
            assertFalse(receipt.contains("\u001b"));
            for(String line:receipt.split("\n")) assertTrue(line.length()<=columns);
            assertTrue(receipt.contains("TOTAL:"));
        }
        assertEquals(27,Receipt.bytes("test")[0]);
    }
    private String backupLedger(String raw) throws Exception {
        JSONObject json=new JSONObject(raw); json.remove("exportedAt"); return json.toString();
    }
}
