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


import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TailerTest {

    @Test
    public void test() throws Exception {
        List<String> lines = new ArrayList<>();
        ByteArrayInputStream in = new ByteArrayInputStream("1\n2\n3".getBytes());

        // ---

        Tailer tailer = new Tailer(in) {

            @Override
            protected boolean isDone() {
                return true;
            }

            @Override
            protected void handle(byte[] buf, int len) {
                lines.add(new String(buf, 0, len));
            }
        };
        tailer.run();

        // ---

        assertEquals(2, lines.size());
        assertEquals("1\n2\n", lines.get(0));
        assertEquals("3", lines.get(1));
    }
}
