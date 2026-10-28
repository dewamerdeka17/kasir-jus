package com.jusmerdeka.pos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Immutable operational report snapshot; no HPP is exported. All dates use WIB. */
public final class ReportData {
    private ReportData() {}
    private static SimpleDateFormat dateFormat() { SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta"));f.setLenient(false);return f; }
    public static JSONObject period(String mode,String anchor) throws Exception {
        if(!anchor.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException("Tanggal laporan tidak valid.");
        SimpleDateFormat f=dateFormat();Calendar start=Calendar.getInstance(f.getTimeZone());start.setTime(f.parse(anchor));
        if(!f.format(start.getTime()).equals(anchor))throw new IllegalArgumentException("Tanggal laporan tidak valid.");
        Calendar end=(Calendar)start.clone();
        switch(mode){case "DAY":break;case "WEEK":start.add(Calendar.DATE,-6);break;case "MONTH":start.set(Calendar.DAY_OF_MONTH,1);end.set(Calendar.DAY_OF_MONTH,end.getActualMaximum(Calendar.DAY_OF_MONTH));break;default:throw new IllegalArgumentException("Periode laporan tidak valid.");}
        return new JSONObject().put("mode",mode).put("start",f.format(start.getTime())).put("end",f.format(end.getTime()));
    }
    public static JSONObject build(JSONObject source,JSONObject period) throws Exception {
        String start=period.getString("start"),end=period.getString("end");
        Map<String,JSONObject> days=new LinkedHashMap<>();SimpleDateFormat f=dateFormat();Calendar c=Calendar.getInstance(f.getTimeZone());c.setTime(f.parse(start));
        while(f.format(c.getTime()).compareTo(end)<=0){String d=f.format(c.getTime());days.put(d,new JSONObject().put("date",d).put("count",0).put("qty",0).put("revenue",0).put("cash",0).put("qris",0).put("legacy",0).put("expenses",0));c.add(Calendar.DATE,1);}
        JSONArray sales=source.getJSONArray("sales"),expenses=source.getJSONArray("expenses"),cash=new JSONArray(),qris=new JSONArray(),other=new JSONArray();
        Map<Long,JSONObject> products=new LinkedHashMap<>();long revenue=0,qty=0,expenseTotal=0,cashTotal=0,qrisTotal=0,legacyTotal=0;int count=0,cancelled=0;
        for(int i=0;i<sales.length();i++){
            JSONObject s=sales.getJSONObject(i);String date=s.getString("created_at").substring(0,10);if(!days.containsKey(date))continue;
            JSONArray items=new JSONArray(s.getString("items"));long units=0;StringBuilder names=new StringBuilder();java.util.Set<Long> seen=new java.util.HashSet<>();
            for(int j=0;j<items.length();j++){JSONObject l=items.getJSONObject(j);units+=l.getLong("qty");if(j>0)names.append("; ");names.append(l.getString("name")).append(" x").append(l.getLong("qty"));}
            JSONObject row=new JSONObject().put("invoice",s.getString("invoice")).put("date",s.getString("created_at")).put("subtotal",s.getLong("subtotal")).put("discount",s.getLong("discount")).put("total",s.getLong("total")).put("paid",s.getLong("paid")).put("change",Math.max(0,s.getLong("paid")-s.getLong("total"))).put("method",s.getString("payment_method")).put("qty",units).put("items",names.toString()).put("status",s.getInt("cancelled")==1?"BATAL":"SAH");
            String method=s.getString("payment_method");boolean valid=s.getInt("cancelled")==0;
            if(!valid){other.put(row);cancelled++;continue;}
            if("CASH".equals(method))cash.put(row);else if("QRIS".equals(method))qris.put(row);else other.put(row);
            count++;long total=s.getLong("total");revenue+=total;qty+=units;
            JSONObject day=days.get(date);day.put("count",day.getLong("count")+1).put("qty",day.getLong("qty")+units).put("revenue",day.getLong("revenue")+total);
            String field="CASH".equals(method)?"cash":"QRIS".equals(method)?"qris":"legacy";day.put(field,day.getLong(field)+total);
            if(field.equals("cash"))cashTotal+=total;else if(field.equals("qris"))qrisTotal+=total;else legacyTotal+=total;
            for(int j=0;j<items.length();j++){
                JSONObject l=items.getJSONObject(j);long id=l.getLong("id");JSONObject p=products.get(id);
                if(p==null){p=new JSONObject().put("id",id).put("name",l.getString("name")).put("qty",0).put("gross",0).put("orders",0);products.put(id,p);}
                p.put("qty",p.getLong("qty")+l.getLong("qty")).put("gross",p.getLong("gross")+l.getLong("qty")*l.getLong("price"));
                if(seen.add(id))p.put("orders",p.getLong("orders")+1);
            }
        }
        JSONArray expenseRows=new JSONArray();
        for(int i=0;i<expenses.length();i++){JSONObject e=expenses.getJSONObject(i);String date=e.getString("expense_date");if(!days.containsKey(date))continue;expenseRows.put(e);if(e.getInt("cancelled")==0){long amount=e.getLong("amount");expenseTotal+=amount;JSONObject day=days.get(date);day.put("expenses",day.getLong("expenses")+amount);}}
        List<JSONObject> sorted=new ArrayList<>(products.values());sorted.sort((a,b)->{int n=Long.compare(b.optLong("qty"),a.optLong("qty"));if(n!=0)return n;n=Long.compare(b.optLong("gross"),a.optLong("gross"));return n!=0?n:a.optString("name").compareTo(b.optString("name"));});
        JSONArray best=new JSONArray();for(JSONObject p:sorted)best.put(p);
        JSONObject busiest=null;JSONArray daily=new JSONArray();
        for(JSONObject d:days.values()){daily.put(d);if(d.getLong("count")>0&&(busiest==null||d.getLong("count")>busiest.getLong("count")||(d.getLong("count")==busiest.getLong("count")&&d.getLong("revenue")>busiest.getLong("revenue"))))busiest=d;}
        JSONObject summary=new JSONObject().put("revenue",revenue).put("count",count).put("qty",qty).put("cash",cashTotal).put("qris",qrisTotal).put("legacy",legacyTotal).put("expenses",expenseTotal).put("afterExpenses",revenue-expenseTotal).put("cancelled",cancelled).put("best",best.length()>0?best.getJSONObject(0):JSONObject.NULL).put("busiest",busiest==null?JSONObject.NULL:busiest);
        return new JSONObject().put("period",period).put("shop",source.getJSONObject("shop")).put("summary",summary).put("daily",daily).put("bestSellers",best).put("cash",cash).put("qris",qris).put("other",other).put("expenses",expenseRows);
    }
}
