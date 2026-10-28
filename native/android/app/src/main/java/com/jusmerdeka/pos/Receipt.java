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
        line(out,shop.optString("header"),columns);
        line(out,sale.getString("invoice"),columns); line(out,sale.getString("created_at"),columns);
        String separator=new String(new char[columns]).replace('\0','-'); line(out,separator,columns);
        JSONArray items=sale.getJSONArray("items");
        for(int i=0;i<items.length();i++) {
            JSONObject l=items.getJSONObject(i); line(out,l.getString("name"),columns);
            line(out,l.getLong("qty")+" x "+money(l.getLong("price"))+" = "+money(l.getLong("price")*l.getLong("qty")),columns);
        }
        line(out,separator,columns); line(out,"Subtotal: "+money(sale.getLong("subtotal")),columns);
        line(out,"Diskon: "+money(sale.getLong("discount")),columns); line(out,"TOTAL: "+money(sale.getLong("total")),columns);
        if("CREDIT".equals(sale.optString("payment_method"))) {
            line(out,"KASBON - BELUM DIBAYAR SAAT BELI",columns); line(out,"Pelanggan: "+sale.optString("customer"),columns);
            line(out,"Kasbon awal: "+money(sale.getLong("total")),columns);
        } else if("QRIS".equals(sale.optString("payment_method"))) {
            line(out,"Metode: QRIS",columns); line(out,"Dibayar: "+money(sale.getLong("total")),columns);
            line(out,"Dikonfirmasi kasir (manual)",columns);
        } else {
            line(out,"Metode: TUNAI",columns);
            line(out,"Tunai: "+money(sale.getLong("paid")),columns); line(out,"Kembali: "+money(sale.getLong("paid")-sale.getLong("total")),columns);
        }
        if(sale.optInt("cancelled")==1) line(out,"*** DIBATALKAN ***",columns);
        line(out,shop.optString("footer"),columns); return out.toString();
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
