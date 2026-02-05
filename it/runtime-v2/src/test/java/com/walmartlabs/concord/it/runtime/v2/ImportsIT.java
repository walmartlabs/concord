package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.common.Posix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;

/**
 * This test class requires its own Concord container because it needs a custom
 * server/agent configuration with {@code disabledProcessors = []} to enable
 * directory imports, which are disabled by default. It cannot share the container
 * with other tests that use the default configuration.
 */
@Execution(ExecutionMode.SAME_THREAD)
public class ImportsIT extends AbstractTest {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure()
            .extraConfigurationSupplier(() -> "concord-server { imports { disabledProcessors = [] } }\n" +
                    "concord-agent { imports { disabledProcessors = [] } }");

    @Test
    public void testDir() throws Exception {
        Path tmpDir = Files.createTempDirectory(ConcordConfiguration.sharedDir(), "test");
        Files.setPosixFilePermissions(tmpDir, Posix.posix(0755));

        Path src = Paths.get(resource("dirImport/other.concord.yml"));
        Path dst = tmpDir.resolve("other.concord.yml");
        Files.copy(src, dst);
        Files.setPosixFilePermissions(dst, Posix.posix(0644));

        String name = "name_" + randomString();
        Payload payload = new Payload()
                .arg("name", name)
                .concordYml("configuration:\n" +
                        "  runtime: concord-v2\n" +
                        "imports:\n" +
                        "  - dir:\n" +
                        "      src: " + dst.getParent().toAbsolutePath() + "\n" +
                        "      dest: concord");

        ConcordProcess proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        proc.assertLog(".*Hello, " + name + ".*");
    }
}
