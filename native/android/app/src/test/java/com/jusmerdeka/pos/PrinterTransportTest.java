package com.jusmerdeka.pos;

import java.io.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PrinterTransportTest {
    @Test public void initializationPrecedesPacedChunksAndConnectionDrain() throws Exception {
        byte[] data=Receipt.bytes("JusMerdeka\n"+"x".repeat(500));ByteArrayOutputStream output=new ByteArrayOutputStream();
        List<String> events=new ArrayList<>();
        OutputStream sink=new OutputStream(){
            @Override public void write(int b){output.write(b);}
            @Override public void write(byte[] b,int off,int len){assertTrue(len<=128);events.add("write:"+len);output.write(b,off,len);}
            @Override public void flush(){events.add("flush");}
            @Override public void close(){fail("Transport must leave socket ownership to caller");}
        };
        PrinterTransport.send(sink,data,ms->events.add("pause:"+ms));
        assertArrayEquals(data,output.toByteArray());assertEquals("write:2",events.get(0));assertEquals("flush",events.get(1));assertEquals("pause:200",events.get(2));
        assertTrue(events.contains("pause:30"));assertEquals("pause:1200",events.get(events.size()-1));
    }
    @Test public void uncertainPartialWriteIsNotRetried() throws Exception {
        int[] calls={0};OutputStream sink=new OutputStream(){
            @Override public void write(int b)throws IOException{throw new IOException();}
            @Override public void write(byte[] b,int off,int len)throws IOException{if(++calls[0]>1)throw new IOException("partial send");}
        };
        try{PrinterTransport.send(sink,Receipt.bytes("test"),ms->{});fail("Write failure swallowed");}catch(IOException expected){}
        assertEquals(2,calls[0]);
    }
    @Test public void oversizedReceiptIsRejectedBeforeAnyBytesAreSent() throws Exception {
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        try{PrinterTransport.send(sink,new byte[65537],ms->{});fail("Oversized receipt sent");}catch(IllegalArgumentException expected){}
        assertEquals(0,sink.size());
    }
}
