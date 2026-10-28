package com.jusmerdeka.pos;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Local role gate. Session is memory-only; closing the activity requires the PIN again. */
public final class OfflineSession {
    private static final Set<String> COMMON = new HashSet<>(Arrays.asList(
        "state", "checkout", "history", "sale", "print", "expenses", "addExpense"));
    private static final Set<String> ADMIN = new HashSet<>(Arrays.asList(
        "reportRange", "exportReport", "previewReceipt", "choosePrinter", "testPrint", "cancelExpense", "saveProduct", "archiveProduct", "cancel", "report", "saveSettings", "backup", "restore"));
    private final SharedPreferences attempts;
    private String role="";

    public OfflineSession(Context context) { attempts=context.getSharedPreferences("pos-login",Context.MODE_PRIVATE); }
    public synchronized String role() { return role; }
    public synchronized boolean isAdmin() { return "ADMIN".equals(role); }
    public synchronized void logout() { role=""; }
    public synchronized String login(String selected,String pin) throws Exception {
        if(!role.isEmpty()) throw new IllegalArgumentException("Keluar dahulu sebelum berganti akun.");
        long remaining=attempts.getLong("blockedUntil",0)-System.currentTimeMillis();
        if(remaining>0) throw new IllegalArgumentException("Terlalu banyak PIN salah. Coba lagi dalam "+((remaining+999)/1000)+" detik.");
        String expected="CASHIER".equals(selected)?"8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92":
            "ADMIN".equals(selected)?"481f6cc0511143ccdd7e2d1b1b94faf0a700a8b49cd13922a70b5ae28acaa8c5":"";
        byte[] actual=MessageDigest.getInstance("SHA-256").digest(pin.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder(); for(byte b:actual) hex.append(String.format(java.util.Locale.US,"%02x",b&255));
        if(!pin.matches("[0-9]{6}")||expected.isEmpty()||!MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.US_ASCII),expected.getBytes(StandardCharsets.US_ASCII))) {
            int failures=attempts.getInt("failures",0)+1;
            attempts.edit().putInt("failures",failures>=5?0:failures)
                .putLong("blockedUntil",failures>=5?System.currentTimeMillis()+30000:0).commit();
            throw new IllegalArgumentException(failures>=5?"PIN salah 5 kali. Tunggu 30 detik sebelum mencoba lagi.":"PIN salah untuk akun yang dipilih.");
        }
        attempts.edit().clear().commit(); role=selected; return role;
    }
    public synchronized void require(String action) {
        if(role.isEmpty()) throw new IllegalArgumentException("Silakan login terlebih dahulu.");
        if(COMMON.contains(action)) return;
        if(ADMIN.contains(action)&&isAdmin()) return;
        throw new IllegalArgumentException("Akses ini hanya untuk admin.");
    }
}
