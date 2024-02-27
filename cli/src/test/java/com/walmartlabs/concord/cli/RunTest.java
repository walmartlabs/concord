package com.walmartlabs.concord.cli;

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

import com.walmartlabs.concord.common.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunTest extends AbstractTest {

    @TempDir
    private Path tempDir;

    @Test
    void runTest() throws Exception {
        Map<String, Object> extraVars = Collections.singletonMap("name", "Concord");
        List<String> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : extraVars.entrySet()) {
            args.add("-e");
            args.add(e.getKey() + "=" + e.getValue());
        }

        int exitCode = run("simple", args);
        assertExitCode(0, exitCode);
        assertLog(".*Hello, Concord.*");
        assertEquals(0, exitCode);
        // default dependencies should be added
        assertLog(".*concord-tasks-" + Version.getVersion() + ".jar.*");
        assertLog(".*http-tasks-" + Version.getVersion() + ".jar.*");
        assertLog(".*slack-tasks-" + Version.getVersion() + ".jar.*");
    }

    @Test
    void testResourceTask() throws Exception {
        int exitCode = run("resourceTask", Collections.emptyList());
        assertExitCode(0, exitCode);
        assertLog(".*\"k\" : \"v\".*");
    }

    @Test
    void testDepsFromProfile() throws Exception {
        int exitCode = run("profileDeps", Arrays.asList("-p", "test"));
        assertExitCode(0, exitCode);
        assertLog(".*exists=true.*");
    }

    @Test
    void testCliCheckpointService() throws Exception {
        int exitCode = run("cliCheckpointService", Collections.emptyList());
        assertExitCode(0, exitCode);
        assertLog(".*Checkpoint.*ignored.*", 2);
    }

    @Test
    void testCustomDefaultConfig() throws Exception {
        int exitCode = run("defaultCfg", Collections.emptyList(), "defaults.yml");
        assertExitCode(0, exitCode);
        assertLog(".*file-tasks-" + Version.getVersion() + ".jar.*");
    }

    @Test
    void testCustomDefaultTaskVars() throws Exception {
        int exitCode = run("defaultTaskVars", List.of("--default-task-vars", tempDir.resolve("defaultTaskVars.json").toString()));
        assertExitCode(0, exitCode);
        assertLog(".*Unknown action: 'customInvalidAction'. Available actions.*");
    }

    @Test
    void testProcessProjectInfo() throws Exception {
        Map<String, Object> extraVars = new HashMap<>();
        extraVars.put("processInfo.sessionToken", "test-token");
        extraVars.put("projectInfo.orgName", "test-org");

        List<String> args = new ArrayList<>();
        for (Map.Entry<String, Object> e : extraVars.entrySet()) {
            args.add("-e");
            args.add(e.getKey() + "=" + e.getValue());
        }

        int exitCode = run("processProjectInfo", args);
        assertExitCode(0, exitCode);
        assertLog(".*processInfo: \\{sessionToken=test-token}.*");
        assertLog(".*projectInfo: \\{orgName=test-org}.*");
    }

    private void assertExitCode(int expected, int current) {
        assertEquals(expected, current, () -> "out:\n" + stdOut() + "\n\n" + "err:\n" + stdErr());
    }

    private int run(String payload, List<String> args) throws Exception {
        return run(payload, args, null);
    }

    private int run(String payload, List<String> args, String defaultCfg) throws Exception {
        URI uri = RunTest.class.getResource(payload).toURI();
        Path source = Paths.get(uri);

        IOUtils.copy(source, tempDir);

        App app = new App();
        CommandLine cmd = new CommandLine(app);

        List<String> effectiveArgs = new ArrayList<>();
        effectiveArgs.add("run");
        effectiveArgs.addAll(args);
        effectiveArgs.add(tempDir.toString());

        if (defaultCfg != null) {
            effectiveArgs.add("--default-cfg");
            effectiveArgs.add(tempDir.resolve(defaultCfg).toString());
        }

        return cmd.execute(effectiveArgs.toArray(new String[0]));
    }
}
