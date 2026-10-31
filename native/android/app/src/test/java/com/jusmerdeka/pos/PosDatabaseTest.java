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
    private long stock() throws Exception {
        JSONArray products=db.products(); for(int i=0;i<products.length();i++) if(products.getJSONObject(i).getLong("id")==id) return products.getJSONObject(i).getLong("stock");
        throw new AssertionError("Test product missing");
    }
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
            assertTrue(receipt.contains(columns==32?"TOTAL ":"TOTAL:"));
        }
        assertEquals(27,Receipt.bytes("test")[0]);
    }
    @Test public void simple58mmReceiptKeepsAmountsAndOmitsUnusedLines() throws Exception {
        JSONObject sale=new JSONObject().put("invoice","JM-001").put("created_at","2026-10-31T14:35:20+07:00")
            .put("subtotal",16000).put("discount",0).put("total",16000).put("paid",20000).put("payment_method","CASH")
            .put("items",new JSONArray().put(new JSONObject().put("name","Jus Semangka").put("qty",2).put("price",8000)));
        JSONObject shop=new JSONObject().put("name","JusMerdeka").put("footer","Terima kasih!");
        String cash=Receipt.format(sale,shop,32);
        assertTrue(cash.contains("31/10/2026 14:35"));
        assertTrue(cash.contains("8.000 x 2")); assertTrue(cash.contains("16.000"));
        assertTrue(cash.startsWith("           JusMerdeka\n"));
        assertFalse(cash.contains("JM-001")); assertFalse(cash.contains("Kasir")); assertFalse(cash.contains("Pembeli"));
        assertTrue(cash.contains("TOTAL 2 QTY"));
        for(String row:cash.split("\n")) { assertTrue(row.length()<=32); if(row.startsWith("Bayar")||row.startsWith("Kembali")||row.startsWith("Tanggal")) assertEquals(32,row.length()); }
        assertTrue(cash.matches("(?s).*Bayar +20\\.000\n.*")); assertTrue(cash.matches("(?s).*Kembali +4\\.000\n.*"));
        assertFalse(cash.contains("Subtotal")); assertFalse(cash.contains("Diskon")); assertFalse(cash.contains("\n\n"));
        String discounted=Receipt.format(sale.put("discount",1000).put("total",15000),shop,32);
        assertTrue(discounted.matches("(?s).*Subtotal +16\\.000\n.*")); assertTrue(discounted.matches("(?s).*Diskon +1\\.000\n.*"));
        String qris=Receipt.format(sale.put("payment_method","QRIS"),shop,32);
        assertTrue(qris.matches("(?s).*Pembayaran +QRIS\n.*")); assertFalse(qris.contains("Kembali"));
        assertTrue(qris.matches("(?s).*Bayar +15\\.000\n.*"));
        String cancelled=Receipt.format(sale.put("cancelled",1).put("items",new JSONArray().put(new JSONObject().put("name","Jus semangka dengan nama menu sangat panjang sekali").put("qty",999).put("price",999999999))),shop.put("footer","Terima kasih atas kunjungan Anda."),32);
        assertTrue(cancelled.contains("*** DIBATALKAN ***")); assertTrue(cancelled.contains("Anda."));
        for(String row:cancelled.split("\n")) assertTrue(row.length()<=32);
        assertFalse(qris.contains("Dikonfirmasi kasir"));
        for(String row:qris.split("\n")) assertTrue(row.length()<=32);
    }
    @Test public void creditCheckoutAndInstallmentsAreAtomicAndIdempotent() throws Exception {
        JSONObject sale=db.checkout(order("credit-sale-request-001",2,0).put("paymentMethod","CREDIT").put("customer","Bu Ani").put("phone","0812345"));
        long saleId=sale.getLong("id");
        assertEquals(3,stock()); assertEquals(19000,db.debt(saleId).getLong("total"));
        JSONObject payment=new JSONObject().put("id",saleId).put("amount",5000).put("requestId","credit-payment-request-001");
        db.payDebt(payment); db.payDebt(payment);
        assertEquals(5000,db.debt(saleId).getLong("paid")); assertEquals(1,db.debt(saleId).getJSONArray("payments").length());
        try { db.payDebt(new JSONObject().put("id",saleId).put("amount",15000).put("requestId","credit-payment-request-002")); fail("Overpayment accepted"); } catch(IllegalArgumentException expected) {}
        try { db.cancel(saleId); fail("Paid debt cancelled without refund"); } catch(IllegalArgumentException expected) {}
        String backup=db.backup(); db.restore(backup);
        assertEquals(5000,db.debt(saleId).getLong("paid"));
        db.payDebt(new JSONObject().put("id",saleId).put("amount",14000).put("requestId","credit-payment-request-003"));
        assertEquals(19000,db.debt(saleId).getLong("paid")); assertEquals(3,stock());
        assertEquals(19000,db.report(sale.getString("created_at").substring(0,10)).getLong("revenue"));
    }
    @Test public void unpaidDebtCancellationReturnsStockAndHidesDebt() throws Exception {
        JSONObject sale=db.checkout(order("credit-sale-request-002",1,0).put("paymentMethod","CREDIT").put("customer","Pak Budi").put("phone",""));
        db.cancel(sale.getLong("id")); db.cancel(sale.getLong("id")); assertEquals(5,stock()); assertEquals(0,db.debts().length());
        try { db.payDebt(new JSONObject().put("id",sale.getLong("id")).put("amount",1000).put("requestId","cancelled-debt-request-001")); fail("Cancelled debt accepted payment"); } catch(IllegalArgumentException expected) {}
    }
    @Test public void corruptDebtBackupIsRejectedWithoutLosingData() throws Exception {
        db.checkout(order("credit-sale-request-003",1,0).put("paymentMethod","CREDIT").put("customer","Ani").put("phone",""));
        String before=db.backup(); JSONObject bad=new JSONObject(before); bad.getJSONArray("debts").getJSONObject(0).put("paid",1000);
        try { db.restore(bad.toString()); fail("Unrecorded installment accepted"); } catch(IllegalArgumentException expected) {}
        assertEquals(backupLedger(before),backupLedger(db.backup()));
    }
    @Test public void oldBackupVersionOneStillRestores() throws Exception {
        db.checkout(order("old-backup-request-001",1,10000));
        JSONObject old=new JSONObject(db.backup()).put("version",1); old.remove("debts"); old.remove("debtPayments");
        db.restore(old.toString()); assertEquals(4,stock()); assertEquals(1,db.history().length());
    }
    @Test public void qrisRequiresConfirmationAndNeverCreatesChange() throws Exception {
        JSONObject input=order("qris-confirm-request-001",2,999999).put("paymentMethod","QRIS");
        try { db.checkoutCash(input); fail("Unconfirmed QRIS accepted"); } catch(IllegalArgumentException expected) {}
        assertEquals(5,stock()); assertEquals(0,db.history().length());
        JSONObject sale=db.checkoutCash(input.put("qrisConfirmed",true));
        assertEquals(19000,sale.getLong("paid")); assertEquals("QRIS",db.sale(sale.getLong("id")).getString("payment_method"));
        assertTrue(sale.getString("receipt").matches("(?s).*Pembayaran +QRIS\n.*")); assertFalse(sale.getString("receipt").contains("Kembali"));
        db.checkoutCash(input); assertEquals(3,stock()); assertEquals(1,db.history().length());
        assertEquals("QRIS",db.history().getJSONObject(0).getString("payment_method"));
    }
    @Test public void receiptEditorPreviewMatchesFormatAndSnapshotsSurviveEditsAndBackup() throws Exception {
        JSONObject shop=db.settings().put("header","SEGAR SETIAP HARI").put("footer","Sampai jumpa!").put("paper","48");
        db.saveSettings(shop);
        String preview=Receipt.preview(PosDatabase.receiptSettings(shop),"QRIS");
        assertTrue(preview.contains("SEGAR SETIAP HARI")); assertTrue(preview.contains("Sampai jumpa!")); assertTrue(preview.contains("QRIS"));
        for(String line:preview.split("\n")) assertTrue(line.length()<=48);
        JSONObject sale=db.checkoutCash(order("receipt-snapshot-request-001",1,10000));
        String oldReceipt=db.sale(sale.getLong("id")).getString("receipt_wide");
        db.saveSettings(shop.put("header","HEADER BARU"));
        assertEquals(oldReceipt,db.sale(sale.getLong("id")).getString("receipt_wide"));
        String backup=db.backup();db.restore(backup);
        assertEquals(oldReceipt,db.sale(sale.getLong("id")).getString("receipt_wide"));
        assertEquals("HEADER BARU",db.settings().getString("header"));
        try { db.saveSettings(shop.put("paper","99")); fail("Unsupported width accepted"); } catch(IllegalArgumentException expected) {}
        assertEquals("48",db.settings().getString("paper"));
    }
    @Test public void versionThreeBackupWithNoPaymentColumnsStillRestores() throws Exception {
        db.checkoutCash(order("old-column-request-001",1,10000));
        JSONObject old=new JSONObject(db.backup()).put("version",3);
        JSONObject sale=old.getJSONArray("sales").getJSONObject(0);sale.remove("payment_method");sale.remove("receipt_wide");
        db.restore(old.toString()); assertEquals("CASH",db.history().getJSONObject(0).getString("payment_method")); assertEquals(4,stock());
    }
    @Test public void editingProductWithoutCostPreservesExistingHistoricalCost() throws Exception {
        db.saveProduct(new JSONObject().put("id",id).put("name","Jus Mangga").put("category","Jus").put("price",11000).put("stock",7));
        JSONArray products=db.products();for(int i=0;i<products.length();i++)if(products.getJSONObject(i).getLong("id")==id)assertEquals(4000,products.getJSONObject(i).getLong("cost"));
    }
    private String backupLedger(String raw) throws Exception {
        JSONObject json=new JSONObject(raw); json.remove("exportedAt"); return json.toString();
    }
}
