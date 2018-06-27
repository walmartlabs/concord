package com.walmartlabs.concord.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */


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
