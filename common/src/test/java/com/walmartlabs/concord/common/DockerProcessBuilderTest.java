package com.walmartlabs.concord.common;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DockerProcessBuilderTest {

    @Test
    void testEtcHostValueIsShellQuoted() throws Exception {
        var host = "example.local:127.0.0.1'; touch /tmp/pwned; echo '";

        try (var process = new DockerProcessBuilder("alpine")
                .forcePull(false)
                .useHostNetwork(false)
                .options(new DockerProcessBuilder.DockerOptionsBuilder()
                        .etcHost(host)
                        .build())
                .build()) {
            var cmd = process.cmd()[2];
            assertTrue(cmd.contains("--add-host " + shellQuote(host)));
            assertFalse(cmd.contains("--add-host " + host));
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "\"'\"") + "'";
    }
}
