package com.jusmerdeka.pos;

import java.io.IOException;
import java.io.OutputStream;

/** ESC/POS transport pacing; a successful write is not a paper-output acknowledgement. */
public final class PrinterTransport {
    private PrinterTransport() {}
    public interface Pause { void waitMillis(long millis) throws InterruptedException; }
    public static void send(OutputStream out,byte[] data,Pause pause) throws IOException,InterruptedException {
        if(data.length<2||data.length>65536) throw new IllegalArgumentException("Ukuran struk tidak didukung.");
        // Let the printer finish initialization before receipt text enters its small receive buffer.
        out.write(data,0,2); out.flush(); pause.waitMillis(200);
        for(int offset=2;offset<data.length;offset+=128) {
            out.write(data,offset,Math.min(128,data.length-offset));out.flush();
            if(offset+128<data.length)pause.waitMillis(30);
        }
        // flush() only handles the local stream. Keep RFCOMM alive for the printer to consume data.
        pause.waitMillis(1200);
    }
}
