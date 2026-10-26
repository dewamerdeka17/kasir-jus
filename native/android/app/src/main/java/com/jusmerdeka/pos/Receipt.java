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
    public static String format(JSONObject sale,JSONObject shop,int columns) throws Exception {
        if(columns!=32&&columns!=48) throw new IllegalArgumentException("Lebar struk tidak valid.");
        StringBuilder out=new StringBuilder();
        for(String key:new String[]{"name","address","phone"}) line(out,shop.optString(key),columns);
        line(out,sale.getString("invoice"),columns); line(out,sale.getString("created_at"),columns);
        String separator=new String(new char[columns]).replace('\0','-'); line(out,separator,columns);
        JSONArray items=sale.getJSONArray("items");
        for(int i=0;i<items.length();i++) {
            JSONObject l=items.getJSONObject(i); line(out,l.getString("name"),columns);
            line(out,l.getLong("qty")+" x "+money(l.getLong("price"))+" = "+money(l.getLong("price")*l.getLong("qty")),columns);
        }
        line(out,separator,columns); line(out,"Subtotal: "+money(sale.getLong("subtotal")),columns);
        line(out,"Diskon: "+money(sale.getLong("discount")),columns); line(out,"TOTAL: "+money(sale.getLong("total")),columns);
        line(out,"Tunai: "+money(sale.getLong("paid")),columns); line(out,"Kembali: "+money(sale.getLong("paid")-sale.getLong("total")),columns);
        if(sale.optInt("cancelled")==1) line(out,"*** DIBATALKAN ***",columns);
        line(out,shop.optString("footer"),columns); return out.toString();
    }
    public static byte[] bytes(String text) {
        return ("\u001b@"+text+"\n\n\n").getBytes(StandardCharsets.US_ASCII);
    }
}
