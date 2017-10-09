package com.walmartlabs.concord.common;

import java.io.IOException;
import java.io.InputStream;

public abstract class Tailer {

    public static final long DEFAULT_DELAY = 500;
    private static final byte DELIMETER = '\n';

    private final InputStream src;
    private final long delay;

    private byte[] line;
    private int pos;

    public Tailer(InputStream src) {
        this(src, DEFAULT_DELAY);
    }

    public Tailer(InputStream src, long delay) {
        this.src = src;
        this.delay = delay;
    }

    protected abstract boolean isDone();

    protected abstract void handle(byte[] buf, int len);

    public void run() throws IOException {
        this.line = new byte[4096];
        this.pos = 0;

        byte[] buf = new byte[4096];
        while (!Thread.currentThread().isInterrupted()) {
            int read = src.read(buf);

            if (read <= 0) {
                // no more data in the stream

                if (isDone()) {
                    // the stream is completed, flush the remaining data
                    flush(pos);
                    return;
                }

                try {
                    // wait for more data
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().isInterrupted();
                }
            } else {
                // we got new data

                while (pos + read >= line.length) {
                    byte[] ab = new byte[line.length * 2];
                    System.arraycopy(line, 0, ab, 0, line.length);
                    line = ab;
                }

                System.arraycopy(buf, 0, line, pos, read);
                pos += read;

                // find the last delimiter
                int x = -1;
                for (int i = read - 1; i >= 0; i--) {
                    if (buf[i] == DELIMETER) {
                        x = i;
                        break;
                    }
                }

                if (x >= 0) {
                    flush(pos - (read - x) + 1);
                }
            }
        }
    }

    private void flush(int len) {
        if (len <= 0) {
            return;
        }

        handle(line, len);

        if (pos > len) {
            // retain the leftovers
            System.arraycopy(line, len, line, 0, pos - len);
            pos = pos - len;
        } else {
            pos = 0;
        }
    }
}
