package com.jusmerdeka.pos;

import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.UUID;

/** Selection is separate from delivery and an admin's confirmation of physical paper output. */
public final class PrinterSelection {
    private final SharedPreferences prefs;
    public PrinterSelection(SharedPreferences prefs){this.prefs=prefs;}
    public synchronized String address(){return prefs.getString("printer","");}
    public synchronized JSONObject state(boolean busy)throws Exception{
        return new JSONObject().put("address",address()).put("name",prefs.getString("printerName","Belum dipilih"))
            .put("testStatus",prefs.getString("printerTestStatus","")).put("testId",prefs.getString("printerTestId","")).put("busy",busy);
    }
    public synchronized void select(String address,String name){
        if(!address.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new IllegalArgumentException("Alamat perangkat tidak valid.");
        if(address.equals(address()))return;
        if(!prefs.edit().putString("printer",address).putString("printerName",name).remove("printerTestStatus").remove("printerTestId").commit())throw new IllegalStateException("Pilihan printer gagal disimpan.");
    }
    public synchronized void clear(){prefs.edit().remove("printer").remove("printerName").remove("printerTestStatus").remove("printerTestId").commit();}
    public synchronized String beginTest(){String id=UUID.randomUUID().toString();prefs.edit().putString("printerTestId",id).putString("printerTestStatus","SENDING").commit();return id;}
    public synchronized void finishTest(String address,String id,boolean sent){
        if(address.equals(address())&&id.equals(prefs.getString("printerTestId","")))prefs.edit().putString("printerTestStatus",sent?"AWAITING":"FAILED").commit();
    }
    public synchronized void confirm(String id,boolean paper){
        if(address().isEmpty()||!id.equals(prefs.getString("printerTestId",""))||!"AWAITING".equals(prefs.getString("printerTestStatus","")))throw new IllegalArgumentException("Tes sudah berubah. Perbarui status dan tes lagi.");
        prefs.edit().putString("printerTestStatus",paper?"CONFIRMED":"NO_PAPER").commit();
    }
}
