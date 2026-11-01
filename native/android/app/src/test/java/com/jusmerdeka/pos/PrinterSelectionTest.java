package com.jusmerdeka.pos;
import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class PrinterSelectionTest {
 private PrinterSelection printer;
 @Before public void setup(){android.content.SharedPreferences prefs=RuntimeEnvironment.getApplication().getSharedPreferences("printer-selection-test",Context.MODE_PRIVATE);prefs.edit().clear().commit();printer=new PrinterSelection(prefs);}
 @Test public void savedSelectionDoesNotClaimSuccessfulPaperOutput() throws Exception{printer.select("AA:BB:CC:DD:EE:01","Iware");assertEquals("",printer.state(false).getString("testStatus"));String id=printer.beginTest();assertEquals("SENDING",printer.state(true).getString("testStatus"));printer.finishTest(printer.address(),id,true);assertEquals("AWAITING",printer.state(false).getString("testStatus"));printer.confirm(id,true);assertEquals("CONFIRMED",printer.state(false).getString("testStatus"));}
 @Test public void noPaperAndFailureRemainDifferentFromConfirmation() throws Exception{printer.select("AA:BB:CC:DD:EE:01","Iware");String id=printer.beginTest();printer.finishTest(printer.address(),id,true);printer.confirm(id,false);assertEquals("NO_PAPER",printer.state(false).getString("testStatus"));id=printer.beginTest();printer.finishTest(printer.address(),id,false);assertEquals("FAILED",printer.state(false).getString("testStatus"));try{printer.confirm(id,true);fail("Failed job was confirmed");}catch(IllegalArgumentException expected){}}
 @Test public void switchingOrClearingPrinterInvalidatesOldTestResults() throws Exception{printer.select("AA:BB:CC:DD:EE:01","Iware");String old=printer.beginTest();printer.select("AA:BB:CC:DD:EE:02","Other");printer.finishTest("AA:BB:CC:DD:EE:01",old,true);assertEquals("",printer.state(false).getString("testStatus"));try{printer.confirm(old,true);fail("Stale test accepted");}catch(IllegalArgumentException expected){}printer.clear();assertEquals("",printer.address());}
}
