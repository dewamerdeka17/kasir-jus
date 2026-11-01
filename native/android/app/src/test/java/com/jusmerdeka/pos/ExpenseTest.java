package com.jusmerdeka.pos;

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
public class ExpenseTest {
    private PosDatabase db;
    @Before public void setup(){RuntimeEnvironment.getApplication().deleteDatabase("jus-offline.db");db=new PosDatabase(RuntimeEnvironment.getApplication());}
    @After public void close(){db.close();}
    private JSONObject input(String key,long amount) throws Exception {
        return new JSONObject().put("request_id",key).put("expense_date","2026-10-26").put("category","BAHAN_BAKU").put("amount",amount).put("description","Beli mangga 5 kg");
    }
    @Test public void recordsPersistAndRetriesDoNotDuplicateExpenses() throws Exception {
        JSONObject p=input("expense-request-0001",50000);long id=db.addExpense(p,"CASHIER").getLong("id");
        db.close();db=new PosDatabase(RuntimeEnvironment.getApplication());assertEquals(id,db.addExpense(p,"CASHIER").getLong("id"));
        assertEquals(50000,db.expenses("2026-10-26","CASHIER").getLong("total"));assertEquals(1,db.expenses("2026-10-26","ADMIN").getJSONArray("items").length());
        assertEquals(-50000,db.report("2026-10-26").getLong("profitAfterExpenses"));
        String backup=db.backup();db.cancelExpense(id);assertEquals(0,db.report("2026-10-26").getLong("expenses"));
        db.restore(backup);assertEquals(50000,db.report("2026-10-26").getLong("expenses"));
    }
    @Test public void roleIsAssignedNativelyAndInvalidInputsAreRejected() throws Exception {
        JSONObject p=input("expense-request-0002",1000).put("created_by","ADMIN");
        assertEquals("CASHIER",db.addExpense(p,"CASHIER").getString("created_by"));
        db.addExpense(input("expense-request-0003",2000),"ADMIN");
        assertEquals(1000,db.expenses("2026-10-26","CASHIER").getLong("total"));assertEquals(3000,db.expenses("2026-10-26","ADMIN").getLong("total"));
        for(JSONObject bad:new JSONObject[]{input("expense-request-0004",0),input("expense-request-0005",1000).put("expense_date","2026-02-30"),input("expense-request-0006",1000).put("category","UNKNOWN")}) {
            try{db.addExpense(bad,"CASHIER");fail("Invalid expense accepted");}catch(Exception expected){}
        }
        assertEquals(3000,db.expenses("2026-10-26","ADMIN").getLong("total"));
    }
    @Test public void invalidBackupDoesNotReplaceCurrentExpenses() throws Exception {
        db.addExpense(input("expense-request-0007",5000),"CASHIER");String backup=db.backup();
        JSONObject bad=new JSONObject(backup);bad.getJSONArray("expenses").getJSONObject(0).put("amount",-1);
        try{db.restore(bad.toString());fail("Invalid backup accepted");}catch(IllegalArgumentException expected){}
        assertEquals(5000,db.expenses("2026-10-26","ADMIN").getLong("total"));
    }
    @Test public void newCreditSalesAreRejectedButExistingDataIsRetained() throws Exception {
        JSONObject order=new JSONObject().put("requestId","removed-credit-request-001").put("paymentMethod","CREDIT");
        try{db.checkoutCash(order);fail("New credit checkout accepted");}catch(IllegalArgumentException expected){}
        assertEquals(0,db.history().length());
    }
}
