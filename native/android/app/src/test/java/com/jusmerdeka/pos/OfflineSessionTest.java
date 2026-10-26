package com.jusmerdeka.pos;

import android.content.Context;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class OfflineSessionTest {
    private Context context;
    @Before public void setup() {
        context=RuntimeEnvironment.getApplication();
        context.getSharedPreferences("pos-login",Context.MODE_PRIVATE).edit().clear().commit();
        context.deleteDatabase("jus-offline.db");
    }
    @Test public void eachRoleNeedsItsOwnPinAndLogoutRequiresReauthentication() throws Exception {
        OfflineSession s=new OfflineSession(context);
        try { s.login("ADMIN","123456"); fail("Cashier PIN accepted for admin"); } catch(IllegalArgumentException expected) {}
        assertEquals("CASHIER",s.login("CASHIER","123456"));
        for(String action:new String[]{"state","checkout","history","sale","print","choosePrinter","testPrint","expenses","addExpense"}) s.require(action);
        for(String action:new String[]{"saveProduct","archiveProduct","cancel","report","saveSettings","backup","restore","cancelExpense","debts","debt","payDebt"}) {
            try { s.require(action); fail("Cashier authorized for "+action); } catch(IllegalArgumentException expected) {}
        }
        try { s.login("ADMIN","654321"); fail("Switched roles without logout"); } catch(IllegalArgumentException expected) {}
        s.logout(); assertEquals("",s.role());
        try { s.require("checkout"); fail("Checkout after logout"); } catch(IllegalArgumentException expected) {}
        assertEquals("ADMIN",s.login("ADMIN","654321")); s.require("restore"); s.require("report");
        assertEquals("",new OfflineSession(context).role());
    }
    @Test public void wrongAttemptsAreLimitedAcrossSessionRecreation() throws Exception {
        for(int i=0;i<5;i++) try { new OfflineSession(context).login("ADMIN","000000"); fail(); } catch(IllegalArgumentException expected) {}
        try { new OfflineSession(context).login("ADMIN","654321"); fail("Cooldown bypassed"); } catch(IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("detik")); }
        context.getSharedPreferences("pos-login",Context.MODE_PRIVATE).edit().putLong("blockedUntil",System.currentTimeMillis()-1).commit();
        assertEquals("ADMIN",new OfflineSession(context).login("ADMIN","654321"));
    }
    @Test public void bridgeEnforcesRolesAndNeverReturnsCostToCashier() throws Exception {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).create().get();
        MainActivity.Bridge bridge=activity.new Bridge();
        assertFalse(new JSONObject(bridge.request("{\"action\":\"state\"}")).getBoolean("ok"));
        assertTrue(new JSONObject(bridge.request("{\"action\":\"login\",\"role\":\"CASHIER\",\"pin\":\"123456\"}")).getBoolean("ok"));
        for(String action:new String[]{"saveProduct","archiveProduct","cancel","report","saveSettings","backup","restore","cancelExpense","debts","debt","payDebt"})
            assertFalse(action,new JSONObject(bridge.request(new JSONObject().put("action",action).toString())).getBoolean("ok"));
        JSONObject state=new JSONObject(bridge.request("{\"action\":\"state\"}")).getJSONObject("data");
        assertFalse(state.getJSONArray("products").getJSONObject(0).has("cost"));
        bridge.request("{\"action\":\"logout\"}");
        assertFalse(new JSONObject(bridge.request("{\"action\":\"history\"}")).getBoolean("ok"));
    }
}
