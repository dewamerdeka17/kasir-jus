package com.jusmerdeka.pos;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Populates the Artifact Tool-authored XLSX template, preserving styles and frozen headers. */
public final class XlsxReport {
    private XlsxReport() {}
    private static final String[] NAMES={"Ringkasan","Harian","Best Seller","Tunai","QRIS","Pengeluaran","Transaksi Lain"};
    private static final String[] METRICS={"Omzet bersih","Transaksi sah","Item terjual","Penjualan Tunai","Penjualan QRIS","Kasbon lama","Pengeluaran aktif","Penjualan - pengeluaran","Rata-rata nota","Best seller","Item best seller","Tanggal teramai","Transaksi tanggal teramai","Transaksi batal"};
    private static final String[] NOTES={"Penjualan sah setelah diskon nota.","Tidak termasuk transaksi batal.","Jumlah unit dalam transaksi sah.","Total penjualan, bukan uang diserahkan sebelum kembalian.","QRIS dikonfirmasi manual oleh kasir.","Hanya catatan dari versi terdahulu.","Tidak termasuk catatan pengeluaran batal.","Bukan laba bersih; tidak menghitung HPP atau perubahan persediaan.","Omzet bersih dibagi jumlah transaksi sah.","Peringkat berdasarkan jumlah unit, lalu subtotal item.","Jumlah unit produk peringkat pertama.","Transaksi terbanyak; seri: omzet terbesar, lalu tanggal awal.","Hanya transaksi sah.","Dicantumkan pada Transaksi Lain dan dikeluarkan dari omzet."};
    private static final class Formula {final String expression;final Number value;Formula(String f,Number v){expression=f;value=v;}}
    public static void write(Context context,JSONObject report,OutputStream destination) throws Exception {
        List<List<Object[]>> sheets=data(report);
        try(InputStream asset=context.getAssets().open("reports/report-template.xlsx");ZipInputStream in=new ZipInputStream(asset);ZipOutputStream out=new ZipOutputStream(destination)) {
            ZipEntry entry;
            while((entry=in.getNextEntry())!=null){
                String name=entry.getName();ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
                byte[] content=bytes.toByteArray();
                if(name.matches("xl/worksheets/sheet[1-7]\\.xml")){
                    int index=Integer.parseInt(name.replaceAll("\\D", ""))-1;
                    content=fill(new String(content,StandardCharsets.UTF_8),index,sheets.get(index),report).getBytes(StandardCharsets.UTF_8);
                }
                if(name.equals("xl/workbook.xml")){
                    String xml=new String(content,StandardCharsets.UTF_8);
                    xml=xml.replaceAll("<x:calcPr\\b[^>]*/>","");xml=xml.replace("</x:workbook>","<x:calcPr calcMode=\"auto\" fullCalcOnLoad=\"1\"/></x:workbook>");content=xml.getBytes(StandardCharsets.UTF_8);
                }
                out.putNextEntry(new ZipEntry(name));out.write(content);out.closeEntry();in.closeEntry();
            }
            out.finish();
        }
    }
    private static String fill(String xml,int index,List<Object[]> values,JSONObject report) throws Exception {
        Map<String,String> styles=new HashMap<>();Matcher cells=Pattern.compile("<x:c\\b([^>]*)>").matcher(xml);
        while(cells.find()){String attrs=cells.group(1);Matcher ref=Pattern.compile("\\br=\"([^\"]+)\"").matcher(attrs),style=Pattern.compile("\\bs=\"(\\d+)\"").matcher(attrs);if(ref.find())styles.put(ref.group(1),style.find()?style.group(1):"0");}
        JSONObject period=report.getJSONObject("period");
        StringBuilder data=new StringBuilder("<x:sheetData>");
        row(data,2,new Object[]{index==0?"Laporan penjualan":NAMES[index]},styles,2,31);
        row(data,3,new Object[]{report.getJSONObject("shop").optString("name","JusMerdeka")},styles,3,24);
        row(data,4,new Object[]{period.getString("start")+" s.d. "+period.getString("end")+" (WIB)"},styles,4,24);
        row(data,5,new Object[]{index==2?"Subtotal item sebelum diskon nota. Sumber: transaksi pada HP kasir.":"Sumber: SQLite pada HP kasir. Semua nominal dalam rupiah."},styles,5,24);
        Matcher header=Pattern.compile("<x:row\\b[^>]*\\br=\"7\"[^>]*>.*?</x:row>",Pattern.DOTALL).matcher(xml);
        if(!header.find())throw new IllegalStateException("Header template tidak ditemukan.");data.append(header.group());
        for(int i=0;i<values.size();i++) {
            Object[] line=values.get(i);int height=index==0?36:32;
            if(index==0&&i==9) height=Math.max(height,16*((String.valueOf(line[1]).length()+24)/25));
            if(index==2) height=Math.max(height,16*((String.valueOf(line[1]).length()+32)/33));
            if(index==5) height=Math.max(height,16*((String.valueOf(line[3]).length()+44)/45));
            if(index==3||index==4||index==6) height=Math.max(height,16*((String.valueOf(line[9]).length()+49)/50));
            row(data,i+8,line,styles,index==0?i+8:8+i%2,Math.min(height,400));
        }
        data.append("</x:sheetData>");
        String result=xml.replaceAll("(?s)<x:sheetData>.*?</x:sheetData>",Matcher.quoteReplacement(data.toString()));
        if(index>0){int last=Math.max(8,values.size()+7);String col=column(index==1?8:index==2?5:index==5?6:index==6?10:9);
            result=result.replace("</x:sheetData>","</x:sheetData><x:autoFilter ref=\"A7:"+col+last+"\"/>");}
        return result;
    }
    private static void row(StringBuilder xml,int row,Object[] values,Map<String,String> styles,int styleRow,int height){
        xml.append("<x:row r=\"").append(row).append("\" ht=\"").append(height).append("\" customHeight=\"1\">");
        for(int c=0;c<values.length;c++){
            String col=column(c);String style=styles.getOrDefault(col+styleRow,"0");Object value=values[c];
            xml.append("<x:c r=\"").append(col).append(row).append("\" s=\"").append(style).append("\"");
            if(value instanceof Formula){Formula f=(Formula)value;xml.append("><x:f>").append(escape(f.expression)).append("</x:f><x:v>").append(f.value).append("</x:v></x:c>");}
            else if(value instanceof Number)xml.append("><x:v>").append(value).append("</x:v></x:c>");
            else xml.append(" t=\"inlineStr\"><x:is><x:t xml:space=\"preserve\">").append(escape(value==null?"":String.valueOf(value))).append("</x:t></x:is></x:c>");
        }
        xml.append("</x:row>");
    }
    private static String escape(String text){return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]","").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String column(int c){return String.valueOf((char)('A'+c));}
    private static double excelDate(String date) throws Exception {
        SimpleDateFormat f=new SimpleDateFormat(date.length()>10?"yyyy-MM-dd HH:mm:ss":"yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String local=date.length()>10?date.substring(0,19).replace('T',' '):date;
        return f.parse(local).getTime()/86400000d+25569d;
    }
    private static String sum(String sheet,String field,int last){return "SUM('"+sheet+"'!"+field+"8:"+field+last+")";}
    private static String byDate(String sheet,String field,int last,int dayRow,boolean legacy){
        return "SUMIFS('"+sheet+"'!"+field+"8:"+field+last+",'"+sheet+"'!B8:B"+last+",\">=\"&A"+dayRow+",'"+sheet+"'!B8:B"+last+",\"<\"&A"+dayRow+"+1"+(legacy?",'Transaksi Lain'!K8:K"+last+",\"SAH\"":"")+")";
    }
    private static String countDate(String sheet,int last,int dayRow,boolean legacy){return "COUNTIFS('"+sheet+"'!B8:B"+last+",\">=\"&A"+dayRow+",'"+sheet+"'!B8:B"+last+",\"<\"&A"+dayRow+"+1"+(legacy?",'Transaksi Lain'!K8:K"+last+",\"SAH\"":"")+")";}
    private static List<List<Object[]>> data(JSONObject report) throws Exception {
        List<List<Object[]>> all=new ArrayList<>();for(int i=0;i<7;i++)all.add(new ArrayList<>());
        JSONArray cash=report.getJSONArray("cash"),qris=report.getJSONArray("qris"),other=report.getJSONArray("other"),expenses=report.getJSONArray("expenses"),daily=report.getJSONArray("daily"),best=report.getJSONArray("bestSellers");
        for(int s=3;s<=6;s++){if(s==5)continue;JSONArray source=s==3?cash:s==4?qris:other;
            for(int i=0;i<source.length();i++){JSONObject r=source.getJSONObject(i);Object[] base={r.getString("invoice"),excelDate(r.getString("date")),r.getLong("subtotal"),r.getLong("discount"),r.getLong("total"),r.getLong("paid"),r.getLong("change"),r.getString("method"),r.getLong("qty"),r.getString("items")};
                if(s==6){Object[] expanded=java.util.Arrays.copyOf(base,11);expanded[10]=r.getString("status");base=expanded;}all.get(s).add(base);}
        }
        Map<String,String> labels=new HashMap<>();labels.put("BAHAN_BAKU","Bahan baku");labels.put("OPERASIONAL","Operasional");labels.put("TRANSPORTASI","Transportasi");labels.put("PERALATAN","Peralatan");labels.put("LAINNYA","Lainnya");
        for(int i=0;i<expenses.length();i++){JSONObject e=expenses.getJSONObject(i);all.get(5).add(new Object[]{e.getLong("id"),excelDate(e.getString("expense_date")),labels.getOrDefault(e.getString("category"),e.getString("category")),e.getString("description"),e.getLong("amount"),e.getString("created_by"),e.getInt("cancelled")==1?"BATAL":"SAH"});}
        int cashLast=Math.max(8,cash.length()+7),qrisLast=Math.max(8,qris.length()+7),otherLast=Math.max(8,other.length()+7),expenseLast=Math.max(8,expenses.length()+7);
        for(int i=0;i<daily.length();i++){JSONObject d=daily.getJSONObject(i);int r=i+8;
            String counts=countDate("Tunai",cashLast,r,false)+"+"+countDate("QRIS",qrisLast,r,false)+"+"+countDate("Transaksi Lain",otherLast,r,true);
            String qty=byDate("Tunai","I",cashLast,r,false)+"+"+byDate("QRIS","I",qrisLast,r,false)+"+"+byDate("Transaksi Lain","I",otherLast,r,true);
            String expense="SUMIFS('Pengeluaran'!E8:E"+expenseLast+",'Pengeluaran'!B8:B"+expenseLast+",A"+r+",'Pengeluaran'!G8:G"+expenseLast+",\"SAH\")";
            all.get(1).add(new Object[]{excelDate(d.getString("date")),new Formula(counts,d.getLong("count")),new Formula(qty,d.getLong("qty")),new Formula("SUM(E"+r+":G"+r+")",d.getLong("revenue")),new Formula(byDate("Tunai","E",cashLast,r,false),d.getLong("cash")),new Formula(byDate("QRIS","E",qrisLast,r,false),d.getLong("qris")),new Formula(byDate("Transaksi Lain","E",otherLast,r,true),d.getLong("legacy")),new Formula(expense,d.getLong("expenses")),new Formula("D"+r+"-H"+r,d.getLong("revenue")-d.getLong("expenses"))});}
        long totalQty=report.getJSONObject("summary").getLong("qty");int bestLast=Math.max(8,best.length()+7);
        for(int i=0;i<best.length();i++){JSONObject p=best.getJSONObject(i);int r=i+8;all.get(2).add(new Object[]{i+1,p.getString("name"),p.getLong("qty"),p.getLong("gross"),p.getLong("orders"),new Formula("IF(SUM(C8:C"+bestLast+")=0,0,C"+r+"/SUM(C8:C"+bestLast+"))",totalQty==0?0:p.getDouble("qty")/totalQty)});}
        JSONObject s=report.getJSONObject("summary");int dailyLast=daily.length()+7;JSONObject top=s.optJSONObject("best"),busy=s.optJSONObject("busiest");
        Object[] metrics={new Formula(sum("Harian","D",dailyLast),s.getLong("revenue")),new Formula(sum("Harian","B",dailyLast),s.getLong("count")),new Formula(sum("Harian","C",dailyLast),s.getLong("qty")),new Formula(sum("Harian","E",dailyLast),s.getLong("cash")),new Formula(sum("Harian","F",dailyLast),s.getLong("qris")),new Formula(sum("Harian","G",dailyLast),s.getLong("legacy")),new Formula(sum("Harian","H",dailyLast),s.getLong("expenses")),new Formula("B8-B14",s.getLong("afterExpenses")),new Formula("IF(B9=0,0,B8/B9)",s.getLong("count")==0?0:s.getDouble("revenue")/s.getLong("count")),top==null?"Belum ada penjualan":top.getString("name"),top==null?0:top.getLong("qty"),busy==null?"Belum ada penjualan":excelDate(busy.getString("date")),busy==null?0:busy.getLong("count"),new Formula("COUNTIFS('Transaksi Lain'!K8:K"+otherLast+",\"BATAL\")",s.getLong("cancelled"))};
        for(int i=0;i<METRICS.length;i++)all.get(0).add(new Object[]{METRICS[i],metrics[i],"  "+NOTES[i]});
        for(int i=1;i<all.size();i++)if(all.get(i).isEmpty()){int cols=i==1?9:i==2?6:i==5?7:i==6?11:10;Object[] empty=new Object[cols];empty[0]="Tidak ada catatan pada periode ini";all.get(i).add(empty);}
        return all;
    }
}
