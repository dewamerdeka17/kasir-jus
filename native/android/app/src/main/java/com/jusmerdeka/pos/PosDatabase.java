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
    private final Context context;
    public PosDatabase(Context context) { super(context, "jus-offline.db", null, 5); this.context=context.getApplicationContext(); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, price INTEGER NOT NULL CHECK(price>=0), cost INTEGER NOT NULL CHECK(cost>=0), stock INTEGER NOT NULL CHECK(stock>=0), active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)))");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, request_id TEXT NOT NULL UNIQUE, invoice TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL, subtotal INTEGER NOT NULL, discount INTEGER NOT NULL, total INTEGER NOT NULL, paid INTEGER NOT NULL, cancelled INTEGER NOT NULL DEFAULT 0 CHECK(cancelled IN (0,1)), receipt TEXT NOT NULL, items TEXT NOT NULL, payment_method TEXT NOT NULL DEFAULT 'CASH', receipt_wide TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        db.execSQL("INSERT INTO settings VALUES ('name','JusMerdeka'), ('address',''), ('phone',''), ('footer','Terima kasih!')");
        createDebtTables(db); createExpenseTable(db); seedCatalog(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion>=1&&newVersion==5) {
            if(oldVersion<2) seedCatalog(db); createDebtTables(db); createExpenseTable(db);
            addSaleColumn(db,"payment_method","TEXT NOT NULL DEFAULT 'CASH'");
            addSaleColumn(db,"receipt_wide","TEXT NOT NULL DEFAULT ''"); return;
        }
        throw new IllegalStateException("Versi database belum didukung. Jangan hapus data aplikasi.");
    }
    private static void addSaleColumn(SQLiteDatabase db,String name,String definition) {
        try(Cursor c=db.rawQuery("PRAGMA table_info(sales)",null)) {
            while(c.moveToNext()) if(name.equals(c.getString(c.getColumnIndexOrThrow("name")))) return;
        }
        db.execSQL("ALTER TABLE sales ADD COLUMN "+name+" "+definition);
    }
    private static void createDebtTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS debts (sale_id INTEGER PRIMARY KEY, customer TEXT NOT NULL, phone TEXT NOT NULL, total INTEGER NOT NULL CHECK(total>0), paid INTEGER NOT NULL DEFAULT 0 CHECK(paid>=0 AND paid<=total), created_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS debt_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, request_id TEXT NOT NULL UNIQUE, sale_id INTEGER NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), created_at TEXT NOT NULL)");
    }
    private static void createExpenseTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, request_id TEXT NOT NULL UNIQUE, expense_date TEXT NOT NULL, category TEXT NOT NULL, amount INTEGER NOT NULL CHECK(amount>0), description TEXT NOT NULL, created_by TEXT NOT NULL, created_at TEXT NOT NULL, cancelled INTEGER NOT NULL DEFAULT 0 CHECK(cancelled IN (0,1)))");
    }
    public synchronized JSONObject checkoutCash(JSONObject input) throws Exception {
        if(!java.util.Arrays.asList("CASH","QRIS").contains(input.optString("paymentMethod","CASH"))) {
            JSONArray previous=rows("SELECT id FROM sales WHERE request_id=?",text(input,"requestId",100));
            if(previous.length()>0) return sale(previous.getJSONObject(0).getLong("id"));
            throw new IllegalArgumentException("Kasbon sudah dihapus. Pilih Tunai atau QRIS.");
        }
        return checkout(input);
    }
    private static void validateExpense(JSONObject p) throws Exception {
        String date=text(p,"expense_date",10);
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd",Locale.US); format.setLenient(false);
        if(!date.matches("\\d{4}-\\d{2}-\\d{2}")||!format.format(format.parse(date)).equals(date)) throw new IllegalArgumentException("Tanggal pengeluaran tidak valid.");
        if(!java.util.Arrays.asList("BAHAN_BAKU","OPERASIONAL","TRANSPORTASI","PERALATAN","LAINNYA").contains(text(p,"category",30))) throw new IllegalArgumentException("Kategori pengeluaran tidak valid.");
        number(p,"amount",1,2_000_000_000);
        if(text(p,"description",300).length()<3) throw new IllegalArgumentException("Keterangan minimal 3 karakter.");
        if(!java.util.Arrays.asList("ADMIN","CASHIER").contains(text(p,"created_by",20))) throw new IllegalArgumentException("Pencatat tidak valid.");
        if(text(p,"request_id",100).length()<16) throw new IllegalArgumentException("ID pengeluaran tidak valid.");
    }
    public synchronized JSONObject addExpense(JSONObject input,String role) throws Exception {
        JSONObject expense=new JSONObject(input.toString()).put("created_by",role); validateExpense(expense);
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONArray existing=rows("SELECT * FROM expenses WHERE request_id=?",expense.getString("request_id"));
            if(existing.length()>0) { db.setTransactionSuccessful(); return existing.getJSONObject(0); }
            ContentValues v=new ContentValues();
            for(String key:new String[]{"request_id","expense_date","category","description","created_by"}) v.put(key,expense.getString(key));
            v.put("amount",expense.getLong("amount")); v.put("created_at",timestamp());
            long id=db.insertOrThrow("expenses",null,v);
            JSONObject result=rows("SELECT * FROM expenses WHERE id=?",""+id).getJSONObject(0); db.setTransactionSuccessful(); return result;
        } finally { db.endTransaction(); }
    }
    public synchronized JSONObject expenses(String date,String role) throws Exception {
        String access="ADMIN".equals(role)?"":" AND created_by='CASHIER'";
        JSONArray list=rows("SELECT * FROM expenses WHERE expense_date=?"+access+" ORDER BY id DESC LIMIT 500",date);
        JSONObject summary=rows("SELECT COALESCE(SUM(amount),0) AS total,COUNT(*) AS count FROM expenses WHERE expense_date=? AND cancelled=0"+access,date).getJSONObject(0);
        return summary.put("items",list);
    }
    public synchronized void cancelExpense(long id) {
        ContentValues v=new ContentValues();v.put("cancelled",1);getWritableDatabase().update("expenses",v,"id=?",new String[]{""+id});
    }
    /** The website seed menu, imported once. Existing and archived names are never overwritten. */
    private void seedCatalog(SQLiteDatabase db) {
        try(java.io.InputStream in=context.getAssets().open("pos/catalog.json"); java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int n; while((n=in.read(bytes))!=-1) out.write(bytes,0,n);
            JSONArray catalog=new JSONArray(out.toString("UTF-8"));
            for(int i=0;i<catalog.length();i++) {
                JSONObject item=catalog.getJSONObject(i);
                try(Cursor existing=db.rawQuery("SELECT id FROM products WHERE lower(trim(name))=lower(trim(?))",new String[]{item.getString("name")})) {
                    if(existing.moveToFirst()) continue;
                }
                ContentValues v=new ContentValues(); v.put("name",item.getString("name")); v.put("category",item.getString("category"));
                v.put("price",item.getLong("price")); v.put("cost",item.getLong("cost")); v.put("stock",0);
                db.insertOrThrow("products",null,v);
            }
        } catch(Exception e) { throw new android.database.sqlite.SQLiteException("Daftar menu bawaan gagal dimuat.",e); }
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
    public static JSONObject receiptSettings(JSONObject input) throws Exception {
        JSONObject result=new JSONObject(input.toString());
        if(!result.has("header")) result.put("header","");
        if(!result.has("paper")) result.put("paper","32");
        for(String key:new String[]{"name","address","phone","header","footer"}) result.put(key,text(result,key,160));
        if(text(result,"name",160).isEmpty()) throw new IllegalArgumentException("Nama toko wajib diisi.");
        if(!java.util.Arrays.asList("32","48").contains(result.getString("paper"))) throw new IllegalArgumentException("Ukuran kertas tidak valid.");
        return result;
    }
    public synchronized void saveSettings(JSONObject input) throws Exception {
        input=receiptSettings(input);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            for (String key : new String[]{"name", "address", "phone", "header", "footer", "paper"}) {
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
            String method=input.optString("paymentMethod","CASH");
            boolean credit="CREDIT".equals(method),qris="QRIS".equals(method);
            if(qris&&!Boolean.TRUE.equals(input.opt("qrisConfirmed"))) throw new IllegalArgumentException("Konfirmasikan pembayaran QRIS sudah diterima.");
            if(!credit&&!qris&&!"CASH".equals(method)) throw new IllegalArgumentException("Metode pembayaran tidak valid.");
            String customer=credit?text(input,"customer",100):"", phone=credit?text(input,"phone",40):"";
            if(credit&&(customer.isEmpty()||total==0)) throw new IllegalArgumentException("Nama pelanggan dan total kasbon wajib diisi.");
            long paid=credit?number(input,"paid",0,0):qris?total:number(input,"paid",total,MAX_MONEY);
            String created=timestamp();
            ContentValues v=new ContentValues();
            v.put("request_id",requestId); v.put("invoice","pending-"+requestId); v.put("created_at",created);
            v.put("subtotal",subtotal); v.put("discount",discount); v.put("total",total); v.put("paid",paid);
            v.put("items",snapshot.toString()); v.put("receipt",""); v.put("payment_method",method);
            long id=db.insertOrThrow("sales",null,v);
            String invoice="JM-"+created.substring(0,10).replace("-","")+"-"+String.format(Locale.US,"%06d",id);
            JSONObject sale=new JSONObject().put("id",id).put("invoice",invoice).put("created_at",created)
                .put("subtotal",subtotal).put("discount",discount).put("total",total).put("paid",paid).put("items",snapshot);
            sale.put("payment_method",method).put("customer",customer);
            if(credit) {
                ContentValues debt=new ContentValues(); debt.put("sale_id",id); debt.put("customer",customer); debt.put("phone",phone);
                debt.put("total",total); debt.put("paid",0); debt.put("created_at",created); db.insertOrThrow("debts",null,debt);
            }
            String receipt=Receipt.format(sale,settings(),32);
            v=new ContentValues(); v.put("invoice",invoice); v.put("receipt",receipt); v.put("receipt_wide",Receipt.format(sale,settings(),48));
            db.update("sales",v,"id=?",new String[]{""+id});
            for(Map.Entry<Long,Long> item:quantities.entrySet())
                db.execSQL("UPDATE products SET stock=stock-? WHERE id=? AND stock>=?",new Object[]{item.getValue(),item.getKey(),item.getValue()});
            db.setTransactionSuccessful(); return sale.put("receipt",receipt).put("cancelled",0);
        } finally { db.endTransaction(); }
    }
    public synchronized JSONArray history() throws Exception {
        return rows("SELECT s.id,s.invoice,s.created_at,s.total,s.paid,s.cancelled,CASE WHEN d.sale_id IS NULL THEN s.payment_method ELSE 'CREDIT' END AS payment_method FROM sales s LEFT JOIN debts d ON d.sale_id=s.id ORDER BY s.id DESC LIMIT 500");
    }
    public synchronized JSONObject sale(long id) throws Exception {
        JSONArray found=rows("SELECT * FROM sales WHERE id=?",""+id);
        if(found.length()!=1) throw new IllegalArgumentException("Transaksi tidak ditemukan.");
        JSONObject sale=found.getJSONObject(0); sale.put("items",new JSONArray(sale.getString("items")));
        JSONArray debt=rows("SELECT * FROM debts WHERE sale_id=?",""+id);
        if(debt.length()>0) sale.put("payment_method","CREDIT");
        if(debt.length()>0) sale.put("customer",debt.getJSONObject(0).getString("customer"));
        return sale;
    }
    public synchronized void cancel(long id) throws Exception {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONObject s=sale(id);
            if(s.getInt("cancelled")==0) {
                JSONArray debt=rows("SELECT paid FROM debts WHERE sale_id=?",""+id);
                if(debt.length()>0&&debt.getJSONObject(0).getLong("paid")>0) throw new IllegalArgumentException("Kasbon sudah menerima pembayaran dan tidak bisa dibatalkan.");
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
    public synchronized JSONArray debts() throws Exception {
        return rows("SELECT d.*,s.invoice FROM debts d JOIN sales s ON s.id=d.sale_id WHERE s.cancelled=0 ORDER BY (d.paid<d.total) DESC,d.sale_id DESC LIMIT 500");
    }
    public synchronized JSONObject debt(long id) throws Exception {
        JSONArray found=rows("SELECT d.*,s.invoice,s.cancelled FROM debts d JOIN sales s ON s.id=d.sale_id WHERE d.sale_id=?",""+id);
        if(found.length()!=1) throw new IllegalArgumentException("Kasbon tidak ditemukan.");
        return found.getJSONObject(0).put("payments",rows("SELECT * FROM debt_payments WHERE sale_id=? ORDER BY id DESC",""+id));
    }
    public synchronized JSONObject payDebt(JSONObject input) throws Exception {
        long id=number(input,"id",1,Long.MAX_VALUE), amount=number(input,"amount",1,MAX_MONEY);
        String key=text(input,"requestId",100);
        if(key.length()<16) throw new IllegalArgumentException("ID pembayaran tidak valid.");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            JSONArray prior=rows("SELECT * FROM debt_payments WHERE request_id=?",key);
            if(prior.length()>0) {
                JSONObject old=prior.getJSONObject(0);
                if(old.getLong("sale_id")!=id||old.getLong("amount")!=amount) throw new IllegalArgumentException("ID pembayaran sudah dipakai.");
                db.setTransactionSuccessful(); return debt(id);
            }
            JSONObject d=debt(id);
            if(d.getInt("cancelled")==1) throw new IllegalArgumentException("Transaksi sudah dibatalkan.");
            if(amount>d.getLong("total")-d.getLong("paid")) throw new IllegalArgumentException("Pembayaran melebihi sisa kasbon.");
            ContentValues v=new ContentValues(); v.put("request_id",key); v.put("sale_id",id); v.put("amount",amount); v.put("created_at",timestamp());
            db.insertOrThrow("debt_payments",null,v);
            db.execSQL("UPDATE debts SET paid=paid+? WHERE sale_id=?",new Object[]{amount,id});
            JSONObject result=debt(id); db.setTransactionSuccessful(); return result;
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
        long expenses=rows("SELECT COALESCE(SUM(amount),0) AS total FROM expenses WHERE expense_date=? AND cancelled=0",day).getJSONObject(0).getLong("total");
        return new JSONObject().put("count",sales.length()).put("revenue",revenue).put("qty",qty).put("profit",revenue-cost).put("expenses",expenses).put("profitAfterExpenses",revenue-cost-expenses);
    }
    public synchronized String backup() throws Exception {
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            String data=new JSONObject().put("format","jus-merdeka-offline").put("version",4).put("exportedAt",timestamp())
                .put("products",rows("SELECT * FROM products")).put("sales",rows("SELECT * FROM sales"))
                .put("debts",rows("SELECT * FROM debts")).put("debtPayments",rows("SELECT * FROM debt_payments")).put("expenses",rows("SELECT * FROM expenses")).put("settings",settings()).toString();
            db.setTransactionSuccessful(); return data;
        } finally { db.endTransaction(); }
    }
    /** Validate the entire document before touching the current ledger. Insert failures roll back. */
    public synchronized void restore(String data) throws Exception {
        JSONObject b=new JSONObject(data);
        if(!"jus-merdeka-offline".equals(b.getString("format"))||number(b,"version",1,4)<1)
            throw new IllegalArgumentException("Bukan backup JusMerdeka offline yang didukung.");
        JSONArray products=b.getJSONArray("products"), sales=b.getJSONArray("sales");
        JSONArray debts=b.getInt("version")>=2?b.getJSONArray("debts"):new JSONArray();
        JSONArray payments=b.getInt("version")>=2?b.getJSONArray("debtPayments"):new JSONArray();
        JSONArray expenses=b.getInt("version")>=3?b.getJSONArray("expenses"):new JSONArray();
        for(int i=0;i<expenses.length();i++) {
            JSONObject e=expenses.getJSONObject(i); validateExpense(e); number(e,"id",1,Long.MAX_VALUE-1); number(e,"cancelled",0,1); text(e,"created_at",40);
        }
        Map<Long,JSONObject> debtIds=new LinkedHashMap<>(); Map<Long,Long> paidSums=new LinkedHashMap<>();
        for(int i=0;i<debts.length();i++) {
            JSONObject d=debts.getJSONObject(i); long id=number(d,"sale_id",1,Long.MAX_VALUE-1);
            if(debtIds.put(id,d)!=null||text(d,"customer",100).isEmpty()) throw new IllegalArgumentException("Kasbon backup tidak valid.");
            text(d,"phone",40); text(d,"created_at",40); long total=number(d,"total",1,MAX_MONEY); number(d,"paid",0,total);
        }
        for(int i=0;i<payments.length();i++) {
            JSONObject payment=payments.getJSONObject(i); number(payment,"id",1,Long.MAX_VALUE-1);
            if(text(payment,"request_id",100).length()<16) throw new IllegalArgumentException("ID pelunasan tidak valid.");
            text(payment,"created_at",40); long id=number(payment,"sale_id",1,Long.MAX_VALUE-1),amount=number(payment,"amount",1,MAX_MONEY);
            if(!debtIds.containsKey(id)) throw new IllegalArgumentException("Kasbon pelunasan tidak ditemukan.");
            long sum=paidSums.getOrDefault(id,0L);
            if(amount>debtIds.get(id).getLong("total")-sum) throw new IllegalArgumentException("Pelunasan backup berlebih.");
            paidSums.put(id,sum+amount);
        }
        for(Long id:debtIds.keySet()) if(debtIds.get(id).getLong("paid")!=paidSums.getOrDefault(id,0L)) throw new IllegalArgumentException("Total pelunasan backup tidak cocok.");
        java.util.Set<Long> saleIds=new java.util.HashSet<>();
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
            long saleId=s.getLong("id"); saleIds.add(saleId);
            JSONObject d=debtIds.get(saleId);
            if(!s.has("payment_method")) s.put("payment_method",d==null?"CASH":"CREDIT");
            if(!s.has("receipt_wide")) s.put("receipt_wide","");
            String method=s.getString("payment_method");
            if(!java.util.Arrays.asList("CASH","QRIS","CREDIT").contains(method)||("CREDIT".equals(method)!=(d!=null))) throw new IllegalArgumentException("Metode pembayaran backup tidak cocok.");
            if("QRIS".equals(method)&&s.getLong("paid")!=total) throw new IllegalArgumentException("Nominal QRIS backup tidak cocok.");
            text(s,"receipt_wide",20000);
            number(s,"paid",d==null?total:0,d==null?MAX_MONEY:0);
            if(d!=null&&(d.getLong("total")!=total||(s.getInt("cancelled")==1&&d.getLong("paid")!=0))) throw new IllegalArgumentException("Total kasbon backup tidak cocok.");
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
        for(Long id:debtIds.keySet()) if(!saleIds.contains(id)) throw new IllegalArgumentException("Transaksi kasbon backup tidak ditemukan.");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("expenses",null,null); db.delete("debt_payments",null,null); db.delete("debts",null,null);
            db.delete("sales",null,null); db.delete("products",null,null);
            for(int i=0;i<products.length();i++) insertRow(db,"products",products.getJSONObject(i),new String[]{"id","name","category","price","cost","stock","active"});
            for(int i=0;i<sales.length();i++) insertRow(db,"sales",sales.getJSONObject(i),new String[]{"id","request_id","invoice","created_at","subtotal","discount","total","paid","cancelled","receipt","items","payment_method","receipt_wide"});
            for(int i=0;i<debts.length();i++) insertRow(db,"debts",debts.getJSONObject(i),new String[]{"sale_id","customer","phone","total","paid","created_at"});
            for(int i=0;i<payments.length();i++) insertRow(db,"debt_payments",payments.getJSONObject(i),new String[]{"id","request_id","sale_id","amount","created_at"});
            for(int i=0;i<expenses.length();i++) insertRow(db,"expenses",expenses.getJSONObject(i),new String[]{"id","request_id","expense_date","category","amount","description","created_by","created_at","cancelled"});
            saveSettings(b.getJSONObject("settings")); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static void insertRow(SQLiteDatabase db,String table,JSONObject row,String[] keys) throws Exception {
        ContentValues v=new ContentValues();
        for(String key:keys) { Object value=row.get(key); if(value instanceof Number) v.put(key,((Number)value).longValue()); else v.put(key,(String)value); }
        db.insertOrThrow(table,null,v);
    }
}
