package com.walmartlabs.concord.agent.logging;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.walmartlabs.concord.agent.logging.ProcessLogTransport.DeliveryStatus.DELIVERED;

public class PlainOutputDecoder implements ProcessOutputDecoder {

    private final ProcessLogTransport transport;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public PlainOutputDecoder(ProcessLogTransport transport) {
        this.transport = transport;
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int len) {
        pending.write(bytes, offset, len);
    }

    @Override
    public synchronized void flush() throws IOException {
        if (pending.size() == 0) {
            return;
        }

        var data = pending.toByteArray();
        if (transport.appendSystem(data) != DELIVERED) {
            throw new IOException("Failed to append process output");
        }

        pending.reset();
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}
