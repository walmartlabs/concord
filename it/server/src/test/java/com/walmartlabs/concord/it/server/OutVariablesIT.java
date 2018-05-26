package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static org.junit.Assert.*;

public class OutVariablesIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("out").toURI());
        String[] out = {"x", "y.some.boolean", "z"};

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(new ByteArrayInputStream(payload), null, true, out);

        Map<String, Object> data = spr.getOut();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
        assertEquals(true, data.get("y.some.boolean"));
        assertFalse(data.containsKey("z"));
    }

    @Test(timeout = 60000)
    public void testPredefined() throws Exception {
        byte[] payload = archive(ProcessIT.class.getResource("out").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("request", Collections.singletonMap("activeProfiles", Arrays.asList("predefinedOut")));

        StartProcessResponse spr = start(input, true);

        Map<String, Object> data = spr.getOut();
        assertNotNull(data);

        assertEquals(123, data.get("x"));
    }
}
