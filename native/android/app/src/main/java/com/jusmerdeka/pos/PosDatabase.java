package com.jusmerdeka.pos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Single-device ledger. All money is integer rupiah; checkout and restore are atomic. */
public final class PosDatabase extends SQLiteOpenHelper {
    private static final long MAX_MONEY = 100_000_000_000_000L;
    public PosDatabase(Context context) { super(context, "jus-offline.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0), cost INTEGER NOT NULL CHECK(cost>=0), stock INTEGER NOT NULL CHECK(stock>=0), active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)))");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, request_id TEXT NOT NULL UNIQUE, invoice TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL, paid INTEGER NOT NULL, cancelled INTEGER NOT NULL DEFAULT 0 CHECK(cancelled IN (0,1)), receipt TEXT NOT NULL, items TEXT NOT NULL)");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("INSERT INTO settings VALUES ('name','JusMerdeka'), ('address',''), ('phone',''), ('footer','Terima kasih!')");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Versi database belum didukung. Jangan hapus data aplikasi.");
    }
    static long number(JSONObject obj, String key, long min, long max) throws Exception {
        Object raw = obj.get(key);
        if (!(raw instanceof Number) || raw instanceof Double || raw instanceof Float)
            throw new IllegalArgumentException(key + " harus angka bulat.");
        long n = ((Number) raw).longValue();
        if (n < min || n > max) throw new IllegalArgumentException(key + " di luar batas.");
        return n;
    }
    static String text(JSONObject obj, String key, int max) throws Exception {
        Object raw = obj.get(key);
        if (!(raw instanceof String)) throw new IllegalArgumentException(key + " harus teks.");
        String s = ((String) raw).trim();
        if (s.length() > max || s.matches("(?s).*[\\p{Cntrl}&&[^\\n\\t]].*"))
            throw new IllegalArgumentException(key + " tidak valid.");
        return s;
    }
    private JSONArray rows(String sql, String... args) throws Exception {
        JSONArray result = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) {
                JSONObject row = new JSONObject();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    row.put(c.getColumnName(i), c.getType(i) == Cursor.FIELD_TYPE_INTEGER ? c.getLong(i) : c.getString(i));
                }
                result.put(row);
            }
        }
        return result;
    }
    public synchronized JSONArray products() throws Exception { return rows("SELECT * FROM products WHERE active=1 ORDER BY name COLLATE NOCASE"); }
    public synchronized JSONObject settings() throws Exception {
        JSONObject result = new JSONObject();
        JSONArray rows = rows("SELECT * FROM settings");
        for (int i=0; i<rows.length(); i++) result.put(rows.getJSONObject(i).getString("key"), rows.getJSONObject(i).getString("value"));
        return result;
    }
    public synchronized void saveSettings(JSONObject input) throws Exception {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (String key : new String[]{"name", "address", "phone", "footer"}) {
                String value = text(input, key, 160);
                if (key.equals("name") && value.isEmpty()) throw new IllegalArgumentException("Nama toko wajib diisi.");
                ContentValues v = new ContentValues(); v.put("key",key); v.put("value",value);
                db.insertWithOnConflict("settings",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public synchronized long saveProduct(JSONObject p) throws Exception {
        String name = text(p,"name",100);
        if (name.isEmpty()) throw new IllegalArgumentException("Nama produk wajib diisi.");
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("category",text(p,"category",60));
        v.put("price",number(p,"price",0,1_000_000_000)); v.put("cost",number(p,"cost",0,1_000_000_000));
        v.put("stock",number(p,"stock",0,1_000_000));
        long id = number(p,"id",0,Long.MAX_VALUE);
        if (id==0) return getWritableDatabase().insertOrThrow("products",null,v);
        if (getWritableDatabase().update("products",v,"id=? AND active=1",new String[]{""+id})!=1)
            throw new IllegalArgumentException("Produk tidak ditemukan.");
        return id;
    }
    public synchronized void archiveProduct(long id) {
        ContentValues v = new ContentValues(); v.put("active",0);
        getWritableDatabase().update("products",v,"id=?",new String[]{""+id});
    }
    private static String timestamp() {
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Jakarta")); return f.format(new Date());
    }
    public synchronized JSONObject checkout(JSONObject input) throws Exception {
        String requestId=text(input,"requestId",100);
        if (requestId.length()<16) throw new IllegalArgumentException("ID pesanan tidak valid.");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONArray prior=rows("SELECT id,invoice,receipt,total,paid,cancelled FROM sales WHERE request_id=?",requestId);
            if(prior.length()>0) { db.setTransactionSuccessful(); return prior.getJSONObject(0); }
            JSONArray items=input.getJSONArray("items");
            if(items.length()<1||items.length()>100) throw new IllegalArgumentException("Keranjang kosong atau terlalu besar.");
            Map<Long,Long> quantities=new LinkedHashMap<>();
            for(int i=0;i<items.length();i++) {
                JSONObject line=items.getJSONObject(i);
                long id=number(line,"id",1,Long.MAX_VALUE), qty=number(line,"qty",1,999);
                qty+=quantities.containsKey(id)?quantities.get(id):0;
                if(qty>999) throw new IllegalArgumentException("Maksimal 999 per produk.");
                quantities.put(id,qty);
            }
            JSONArray snapshot=new JSONArray(); long subtotal=0;
            for(Map.Entry<Long,Long> item:quantities.entrySet()) {
                JSONArray found=rows("SELECT * FROM products WHERE id=? AND active=1",""+item.getKey());
                if(found.length()!=1) throw new IllegalArgumentException("Produk sudah tidak tersedia.");
                JSONObject p=found.getJSONObject(0); long qty=item.getValue();
                if(p.getLong("stock")<qty) throw new IllegalArgumentException("Stok "+p.getString("name")+" tidak cukup.");
                subtotal+=p.getLong("price")*qty;
                snapshot.put(new JSONObject().put("id",p.getLong("id")).put("name",p.getString("name"))
                    .put("qty",qty).put("price",p.getLong("price")).put("cost",p.getLong("cost")));
            }
            long discount=number(input,"discount",0,subtotal), total=subtotal-discount;
            long paid=number(input,"paid",total,MAX_MONEY);
            String created=timestamp();
            ContentValues v=new ContentValues();
            v.put("request_id",requestId); v.put("invoice","pending-"+requestId); v.put("created_at",created);
            v.put("subtotal",subtotal); v.put("discount",discount); v.put("total",total); v.put("paid",paid);
            v.put("items",snapshot.toString()); v.put("receipt","");
            long id=db.insertOrThrow("sales",null,v);
            String invoice="JM-"+created.substring(0,10).replace("-","")+"-"+String.format(Locale.US,"%06d",id);
            JSONObject sale=new JSONObject().put("id",id).put("invoice",invoice).put("created_at",created)
                .put("subtotal",subtotal).put("discount",discount).put("total",total).put("paid",paid).put("items",snapshot);
            String receipt=Receipt.format(sale,settings(),32);
            v=new ContentValues(); v.put("invoice",invoice); v.put("receipt",receipt);
            db.update("sales",v,"id=?",new String[]{""+id});
            for(Map.Entry<Long,Long> item:quantities.entrySet())
                db.execSQL("UPDATE products SET stock=stock-? WHERE id=? AND stock>=?",new Object[]{item.getValue(),item.getKey(),item.getValue()});
            db.setTransactionSuccessful(); return sale.put("receipt",receipt).put("cancelled",0);
        } finally { db.endTransaction(); }
    }
    public synchronized JSONArray history() throws Exception {
        return rows("SELECT id,invoice,created_at,total,paid,cancelled FROM sales ORDER BY id DESC LIMIT 500");
    }
    public synchronized JSONObject sale(long id) throws Exception {
        JSONArray found=rows("SELECT * FROM sales WHERE id=?",""+id);
        if(found.length()!=1) throw new IllegalArgumentException("Transaksi tidak ditemukan.");
        JSONObject sale=found.getJSONObject(0); sale.put("items",new JSONArray(sale.getString("items"))); return sale;
    }
    public synchronized void cancel(long id) throws Exception {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject s=sale(id);
            if(s.getInt("cancelled")==0) {
                db.execSQL("UPDATE sales SET cancelled=1 WHERE id=? AND cancelled=0",new Object[]{id});
                JSONArray lines=s.getJSONArray("items");
                for(int i=0;i<lines.length();i++) {
                    JSONObject line=lines.getJSONObject(i);
                    db.execSQL("UPDATE products SET stock=stock+? WHERE id=?",new Object[]{line.getLong("qty"),line.getLong("id")});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    public synchronized JSONObject report(String day) throws Exception {
        if(!day.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Tanggal tidak valid.");
        JSONArray sales=rows("SELECT total,items FROM sales WHERE cancelled=0 AND substr(created_at,1,10)=?",day);
        long revenue=0,cost=0,qty=0;
        for(int i=0;i<sales.length();i++) {
            JSONObject s=sales.getJSONObject(i); revenue+=s.getLong("total"); JSONArray lines=new JSONArray(s.getString("items"));
            for(int j=0;j<lines.length();j++) { JSONObject l=lines.getJSONObject(j); qty+=l.getLong("qty"); cost+=l.getLong("cost")*l.getLong("qty"); }
        }
        return new JSONObject().put("count",sales.length()).put("revenue",revenue).put("qty",qty).put("profit",revenue-cost);
    }
    public synchronized String backup() throws Exception {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            String data=new JSONObject().put("format","jus-merdeka-offline").put("version",1).put("exportedAt",timestamp())
                .put("products",rows("SELECT * FROM products")).put("sales",rows("SELECT * FROM sales"))
                .put("settings",settings()).toString();
            db.setTransactionSuccessful(); return data;
        } finally { db.endTransaction(); }
    }
    /** Validate the entire document before touching the current ledger. Insert failures roll back. */
    public synchronized void restore(String data) throws Exception {
        JSONObject b=new JSONObject(data);
        if(!"jus-merdeka-offline".equals(b.getString("format"))||number(b,"version",1,1)!=1)
            throw new IllegalArgumentException("Bukan backup JusMerdeka offline versi 1.");
        JSONArray products=b.getJSONArray("products"), sales=b.getJSONArray("sales");
        if(products.length()>10000||sales.length()>100000) throw new IllegalArgumentException("Backup terlalu besar.");
        Map<Long,Boolean> productIds=new LinkedHashMap<>();
        for(int i=0;i<products.length();i++) {
            JSONObject p=products.getJSONObject(i); long id=number(p,"id",1,Long.MAX_VALUE-1);
            if(productIds.put(id,true)!=null) throw new IllegalArgumentException("ID produk duplikat.");
            if(text(p,"name",100).isEmpty()) throw new IllegalArgumentException("Nama produk kosong.");
            text(p,"category",60); number(p,"price",0,1_000_000_000); number(p,"cost",0,1_000_000_000);
            number(p,"stock",0,Integer.MAX_VALUE); number(p,"active",0,1);
        }
        for(int i=0;i<sales.length();i++) {
            JSONObject s=sales.getJSONObject(i); number(s,"id",1,Long.MAX_VALUE-1); number(s,"cancelled",0,1);
            if(text(s,"request_id",100).length()<16||text(s,"invoice",100).isEmpty()) throw new IllegalArgumentException("Identitas transaksi tidak valid.");
            String date=text(s,"created_at",40);
            if(!date.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+07:00")) throw new IllegalArgumentException("Waktu transaksi tidak valid.");
            text(s,"receipt",20000); long subtotal=number(s,"subtotal",0,MAX_MONEY);
            long discount=number(s,"discount",0,subtotal),total=number(s,"total",0,MAX_MONEY);
            if(total!=subtotal-discount) throw new IllegalArgumentException("Total backup tidak cocok.");
            number(s,"paid",total,MAX_MONEY);
            JSONArray lines=new JSONArray(s.getString("items")); long sum=0;
            if(lines.length()<1||lines.length()>100) throw new IllegalArgumentException("Item backup tidak valid.");
            for(int j=0;j<lines.length();j++) {
                JSONObject l=lines.getJSONObject(j);
                if(!productIds.containsKey(number(l,"id",1,Long.MAX_VALUE-1))) throw new IllegalArgumentException("Produk backup tidak lengkap.");
                text(l,"name",100); long q=number(l,"qty",1,999),p=number(l,"price",0,1_000_000_000);
                number(l,"cost",0,1_000_000_000); sum+=q*p;
            }
            if(sum!=subtotal) throw new IllegalArgumentException("Subtotal backup tidak cocok.");
        }
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("sales",null,null); db.delete("products",null,null);
            for(int i=0;i<products.length();i++) insertRow(db,"products",products.getJSONObject(i),new String[]{"id","name","category","price","cost","stock","active"});
            for(int i=0;i<sales.length();i++) insertRow(db,"sales",sales.getJSONObject(i),new String[]{"id","request_id","invoice","created_at","subtotal","discount","total","paid","cancelled","receipt","items"});
            saveSettings(b.getJSONObject("settings")); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static void insertRow(SQLiteDatabase db,String table,JSONObject row,String[] keys) throws Exception {
        ContentValues v=new ContentValues();
        for(String key:keys) { Object value=row.get(key); if(value instanceof Number) v.put(key,((Number)value).longValue()); else v.put(key,(String)value); }
        db.insertOrThrow(table,null,v);
    }
}
