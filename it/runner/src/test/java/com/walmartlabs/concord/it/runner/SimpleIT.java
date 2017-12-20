package com.walmartlabs.concord.it.runner;

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

import org.junit.Test;

import java.nio.file.Paths;
import java.util.UUID;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;

public class SimpleIT extends AbstractRunnerIT {

    @Test
    public void test() throws Exception {
        String instanceId = UUID.randomUUID().toString();
        Process proc = exec(instanceId, Paths.get(SimpleIT.class.getResource("simple").toURI()));

        byte[] ab = readLog(proc);
        System.out.println(new String(ab));

        int code = proc.waitFor();
        assertEquals(0, code);

        assertEquals(1, grep(".*Hello, Concord.*", ab).size());
    }
}
