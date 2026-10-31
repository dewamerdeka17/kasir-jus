package com.jusmerdeka.pos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;

public final class Receipt {
    private Receipt() {}
    public static String money(long value) { return "Rp"+NumberFormat.getIntegerInstance(new Locale("id","ID")).format(value); }
    private static void line(StringBuilder out,String value,int columns) {
        // No user supplied ESC/POS commands or unsupported control characters reach the printer.
        String clean=value.replaceAll("[^\\x20-\\x7E]"," ");
        if(clean.isEmpty()) { out.append('\n'); return; }
        for(int i=0;i<clean.length();i+=columns) out.append(clean,i,Math.min(i+columns,clean.length())).append('\n');
    }
    private static void optional(StringBuilder out,String value,int columns) {
        if(!value.trim().isEmpty()) line(out,value,columns);
    }
    private static void pair(StringBuilder out,String left,String right,int columns) {
        int gap=columns-left.length()-right.length();
        if(gap<1) { line(out,left,columns); line(out,right,columns); }
        else line(out,left+new String(new char[gap]).replace('\0',' ')+right,columns);
    }
    private static String shortDate(String value) {
        // Keep the phone's recorded local time, without converting time zones.
        if(value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*"))
            return value.substring(8,10)+"/"+value.substring(5,7)+"/"+value.substring(0,4)+" "+value.substring(11,16);
        return value;
    }
    public static String format(JSONObject sale,JSONObject shop,int columns) throws Exception {
        if(columns!=32&&columns!=48) throw new IllegalArgumentException("Lebar struk tidak valid.");
        StringBuilder out=new StringBuilder();
        for(String key:new String[]{"name","address","phone"}) optional(out,shop.optString(key),columns);
        optional(out,shop.optString("header"),columns);
        line(out,sale.getString("invoice"),columns); line(out,columns==32?shortDate(sale.getString("created_at")):sale.getString("created_at"),columns);
        String separator=new String(new char[columns]).replace('\0','-'); line(out,separator,columns);
        JSONArray items=sale.getJSONArray("items");
        for(int i=0;i<items.length();i++) {
            JSONObject l=items.getJSONObject(i); line(out,l.getString("name"),columns);
            if(columns==32) pair(out,l.getLong("qty")+" x "+money(l.getLong("price")),money(l.getLong("price")*l.getLong("qty")),columns);
            else line(out,l.getLong("qty")+" x "+money(l.getLong("price"))+" = "+money(l.getLong("price")*l.getLong("qty")),columns);
        }
        line(out,separator,columns); if(columns!=32||sale.getLong("discount")>0) line(out,"Subtotal: "+money(sale.getLong("subtotal")),columns);
        if(columns!=32||sale.getLong("discount")>0) line(out,"Diskon: "+money(sale.getLong("discount")),columns); line(out,"TOTAL: "+money(sale.getLong("total")),columns);
        if("CREDIT".equals(sale.optString("payment_method"))) {
            line(out,"KASBON - BELUM DIBAYAR SAAT BELI",columns); line(out,"Pelanggan: "+sale.optString("customer"),columns);
            line(out,"Kasbon awal: "+money(sale.getLong("total")),columns);
        } else if("QRIS".equals(sale.optString("payment_method"))) {
            line(out,"Metode: QRIS",columns); line(out,"Dibayar: "+money(sale.getLong("total")),columns);
            if(columns!=32) line(out,"Dikonfirmasi kasir (manual)",columns);
        } else {
            if(columns!=32) line(out,"Metode: TUNAI",columns);
            line(out,"Tunai: "+money(sale.getLong("paid")),columns); line(out,"Kembali: "+money(sale.getLong("paid")-sale.getLong("total")),columns);
        }
        if(sale.optInt("cancelled")==1) line(out,"*** DIBATALKAN ***",columns);
        optional(out,shop.optString("footer"),columns); return out.toString();
    }
    public static String preview(JSONObject settings,String method) throws Exception {
        if(!"QRIS".equals(method)&&!"CASH".equals(method)) throw new IllegalArgumentException("Metode contoh tidak valid.");
        JSONObject sale=new JSONObject().put("invoice","CONTOH-STRUK").put("created_at","2026-10-28T10:00:00+07:00")
            .put("subtotal",20000).put("discount",1000).put("total",19000).put("paid","QRIS".equals(method)?19000:20000)
            .put("payment_method",method).put("items",new JSONArray().put(new JSONObject().put("name","Jus Semangka").put("qty",1).put("price",8000))
                .put(new JSONObject().put("name","Jus Mangga").put("qty",1).put("price",12000)));
        return format(sale,settings,Integer.parseInt(settings.optString("paper","32")));
    }
    public static byte[] bytes(String text) {
        return ("\u001b@"+text+"\n\n\n").getBytes(StandardCharsets.US_ASCII);
    }
}
