package com.jusmerdeka.pos;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class ReportTest {
    private JSONObject sale(long id,String date,String method,long qty,long price,long discount,boolean cancelled) throws Exception {
        return new JSONObject().put("id",id).put("invoice","JM-TEST-"+id).put("created_at",date+"T12:00:00+07:00").put("subtotal",qty*price).put("discount",discount).put("total",qty*price-discount).put("paid",qty*price-discount).put("cancelled",cancelled?1:0).put("payment_method",method)
            .put("items",new JSONArray().put(new JSONObject().put("id",1).put("name","Jus Mangga").put("qty",qty).put("price",price).put("cost",1234)).toString());
    }
    private JSONObject sample() throws Exception {
        JSONArray sales=new JSONArray().put(sale(1,"2026-10-22","CASH",2,12000,1000,false)).put(sale(2,"2026-10-23","QRIS",1,12000,0,false)).put(sale(3,"2026-10-23","CASH",1,12000,0,false)).put(sale(4,"2026-10-23","QRIS",99,12000,0,true)).put(sale(5,"2026-10-21","CASH",999,12000,0,false)).put(sale(6,"2026-10-29","CASH",999,12000,0,false));
        JSONArray expenses=new JSONArray().put(new JSONObject().put("id",1).put("expense_date","2026-10-23").put("category","BAHAN_BAKU").put("description","=HYPERLINK(\"test\")").put("amount",5000).put("created_by","CASHIER").put("cancelled",0));
        return ReportData.build(new JSONObject().put("sales",sales).put("expenses",expenses).put("shop",new JSONObject().put("name","JusMerdeka CONTOH")),ReportData.period("WEEK","2026-10-28"));
    }
    @Test public void selectedRangesRespectInclusiveDaysAndCalendarMonths() throws Exception {
        assertEquals("2026-10-22",ReportData.period("WEEK","2026-10-28").getString("start"));
        assertEquals("2024-02-29",ReportData.period("MONTH","2024-02-14").getString("end"));
        assertEquals("2026-10-28",ReportData.period("DAY","2026-10-28").getString("start"));
        try{ReportData.period("DAY","2026-02-30");fail("Invalid date accepted");}catch(Exception expected){}
    }
    @Test public void summarySplitsMethodsExcludesCancelledAndRanksCorrectly() throws Exception {
        JSONObject r=sample(),s=r.getJSONObject("summary");
        assertEquals(47000,s.getLong("revenue"));assertEquals(35000,s.getLong("cash"));assertEquals(12000,s.getLong("qris"));assertEquals(3,s.getLong("count"));assertEquals(4,s.getLong("qty"));assertEquals(1,s.getLong("cancelled"));
        assertEquals("2026-10-23",s.getJSONObject("busiest").getString("date"));assertEquals(4,s.getJSONObject("best").getLong("qty"));
        assertEquals(48000,s.getJSONObject("best").getLong("gross"));assertEquals(42000,s.getLong("afterExpenses"));assertEquals(7,r.getJSONArray("daily").length());
    }
    @Test public void xlsxIsValidTypedStyledAndHasSeparatedWorksheets() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();XlsxReport.write(RuntimeEnvironment.getApplication(),sample(),bytes);
        Map<String,String> entries=new HashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes.toByteArray()))){ZipEntry entry;byte[] buf=new byte[8192];while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buf))!=-1)out.write(buf,0,n);if(entry.getName().endsWith(".xml")){DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.newDocumentBuilder().parse(new ByteArrayInputStream(out.toByteArray()));entries.put(entry.getName(),out.toString("UTF-8"));}}}
        assertTrue(entries.get("xl/workbook.xml").contains("Best Seller"));assertTrue(entries.get("xl/workbook.xml").contains("QRIS"));
        assertTrue(entries.get("xl/worksheets/sheet1.xml").contains("47000"));
        assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("SUMIFS"));assertTrue(entries.get("xl/worksheets/sheet2.xml").contains("frozen"));
        assertFalse(entries.get("xl/worksheets/sheet4.xml").contains("JM-TEST-2"));assertTrue(entries.get("xl/worksheets/sheet5.xml").contains("JM-TEST-2"));
        assertTrue(entries.get("xl/worksheets/sheet6.xml").contains("inlineStr"));assertFalse(entries.get("xl/worksheets/sheet6.xml").contains("<x:f>HYPERLINK"));
        java.io.File dir=new java.io.File("build/reports");dir.mkdirs();try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"sample-export.xlsx"))){out.write(bytes.toByteArray());}
    }
    @Test public void emptyReportsHaveNoInventedBestSellerOrBusiestDay() throws Exception {
        JSONObject r=ReportData.build(new JSONObject().put("sales",new JSONArray()).put("expenses",new JSONArray()).put("shop",new JSONObject()),ReportData.period("MONTH","2026-10-28"));
        assertEquals(31,r.getJSONArray("daily").length());assertTrue(r.getJSONObject("summary").isNull("best"));assertTrue(r.getJSONObject("summary").isNull("busiest"));
        XlsxReport.write(RuntimeEnvironment.getApplication(),r,new ByteArrayOutputStream());
    }
}
