package com.jusmerdeka.pos;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class CatalogTest {
    private Context context;
    @Before public void setup() { context=RuntimeEnvironment.getApplication(); context.deleteDatabase("jus-offline.db"); }
    @Test public void includesAllSeventeenWebsiteSeedProductsWithNoInventedStock() throws Exception {
        try(PosDatabase db=new PosDatabase(context)) {
            JSONArray products=db.products(); assertEquals(17,products.length()); int mixes=0;
            for(int i=0;i<products.length();i++) {
                JSONObject p=products.getJSONObject(i); assertEquals(0,p.getLong("stock"));
                if(p.getString("category").equals("Jus Mix")) { mixes++; assertEquals(12000,p.getLong("price")); }
                if(p.getString("name").equals("Jus Alpukat")) assertEquals(13000,p.getLong("price"));
            }
            assertEquals(2,mixes);
        }
    }
    @Test public void upgradePreservesExistingPricesStocksAndSalesAndDoesNotDuplicateMenu() throws Exception {
        try(PosDatabase db=new PosDatabase(context)) { db.getWritableDatabase(); }
        try(SQLiteDatabase raw=context.openOrCreateDatabase("jus-offline.db",0,null)) {
            raw.execSQL("DELETE FROM products");
            raw.execSQL("INSERT INTO products(name,category,price,cost,stock,active) VALUES ('Jus Mangga','Khusus',15000,6500,7,1)");
            raw.execSQL("DROP TABLE debts"); raw.execSQL("DROP TABLE debt_payments"); raw.setVersion(1);
        }
        try(PosDatabase upgraded=new PosDatabase(context)) {
            JSONArray products=upgraded.products(); assertEquals(17,products.length());
            for(int i=0;i<products.length();i++) { JSONObject p=products.getJSONObject(i); if(p.getString("name").equals("Jus Mangga")) {
                assertEquals(15000,p.getLong("price")); assertEquals(7,p.getLong("stock")); assertEquals("Khusus",p.getString("category"));
            }}
        }
        try(PosDatabase reopened=new PosDatabase(context)) { assertEquals(17,reopened.products().length()); }
    }
}
