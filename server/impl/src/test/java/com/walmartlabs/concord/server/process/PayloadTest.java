package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.process.keys.HeaderKey;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class PayloadTest {

    private static final HeaderKey<String> MY_STRING_HEADER = HeaderKey.register("myStringHeader", String.class);

    @Test
    public void test() throws Exception {
        Payload p = new Payload(UUID.randomUUID())
                .putHeader(MY_STRING_HEADER, "hello")
                .putHeaders(Collections.singletonMap("a", 1));

        assertEquals("hello", p.getHeader(MY_STRING_HEADER));
        assertEquals(new Integer(1), p.getHeader("a"));
    }
}
