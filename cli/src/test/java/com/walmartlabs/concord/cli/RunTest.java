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
import com.walmartlabs.concord.common.TemporaryPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class RunTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @BeforeEach
    public void setUpStreams() {
        out.reset();
        err.reset();
        System.setOut(new PrintStream(out));
        System.setErr(new PrintStream(err));
    }

    @AfterEach
    public void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    public void runTest() throws Exception {
        int exitCode = run("simple", Collections.singletonMap("name", "Concord"));
        assertEquals(0, exitCode);
        assertLog(".*Hello, Concord.*");
    }

    @Test
    public void testResourceTask() throws Exception {
        int exitCode = run("resourceTask", Collections.emptyMap());
        assertEquals(0, exitCode);
        assertLog(".*\"k\" : \"v\".*");
    }

    private static int run(String payload, Map<String, Object> extraVars) throws Exception {
        URI uri = RunTest.class.getResource(payload).toURI();
        Path source = Paths.get(uri);

        try (TemporaryPath dst = IOUtils.tempDir("cli-tests")) {
            IOUtils.copy(source, dst.path());

            App app = new App();
            CommandLine cmd = new CommandLine(app);

            List<String> args = new ArrayList<>();
            args.add("run");
            for (Map.Entry<String, Object> e : extraVars.entrySet()) {
                args.add("-e");
                args.add(e.getKey() + "=" + e.getValue());
            }
            args.add(dst.path().toString());

            return cmd.execute(args.toArray(new String[0]));
        }
    }

    private void assertLog(String pattern) {
        String outStr = out.toString();
        if (grep(outStr, pattern) != 1) {
            fail("Expected a single log entry: '" + pattern + "', got: \n" + outStr);
        }
    }

    private static int grep(String str, String pattern) {
        int cnt = 0;

        String[] lines = str.split("\\r?\\n");
        for (String line : lines) {
            if (line.matches(pattern)) {
                cnt++;
            }
        }

        return cnt;
    }
}
