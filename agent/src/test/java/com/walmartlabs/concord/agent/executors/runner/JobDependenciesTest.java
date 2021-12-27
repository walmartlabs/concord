package com.walmartlabs.concord.agent.executors.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class JobDependenciesTest {

    @Test
    public void test() throws Exception {
        Path payloadDir = Files.createTempDirectory("test");

        Path versionsFile = payloadDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(Constants.Files.DEPENDENCY_VERSIONS_FILE_NAME);

        Files.createDirectories(versionsFile.getParent());

        try (InputStream in = JobDependenciesTest.class.getResourceAsStream("versions.properties")) {
            Files.copy(in, versionsFile);
        }

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.DEPENDENCIES_KEY, Arrays.asList(
                "file://something.jar",
                "mvn://org.codehaus.groovy:groovy-all:pom:2.5.8",
                "mvn://aaa:aaa:1.0",
                "mvn://bbb:bbb:latest",
                "mvn://ccc:ccc:1.0.1-20190214.203609-21",
                "mvn://ddd:ddd:latest"
        ));

        RunnerJob j = mock(RunnerJob.class);
        when(j.getProcessCfg()).thenReturn(cfg);
        when(j.getPayloadDir()).thenReturn(payloadDir);

        RunnerLog log = mock(RunnerLog.class);
        when(j.getLog()).thenReturn(log);

        Collection<URI> uris = JobDependencies.get(j);
        assertEquals(6, uris.size());

        assertContains("mvn://aaa:aaa:1.0", uris);
        assertContains("mvn://bbb:bbb:1.0", uris);
        assertContains("mvn://ccc:ccc:1.0.1-20190214.203609-21", uris);
        assertContains("mvn://ddd:ddd:latest", uris);

        verify(log, times(1)).warn(anyString(), any());
    }

    private static void assertContains(String s, Collection<URI> uris) {
        for (URI u : uris) {
            if (u.toString().equals(s)) {
                return;
            }
        }

        fail("Expected to find " + s);
    }
}
