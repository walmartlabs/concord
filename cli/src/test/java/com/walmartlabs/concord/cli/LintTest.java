package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LintTest extends AbstractTest {

    @Test
    void lintV1Test() throws Exception {
        int exitCode = lint("lintV1");
        assertEquals(0, exitCode);
        assertLog(".*flows: 2.*");
    }

    @Test
    void lintV2Test() throws Exception {
        int exitCode = lint("lintV2");
        assertEquals(0, exitCode);
        assertLog(".*flows: 2.*");
    }

    @Test
    void lintV25Test() throws Exception {
        int exitCode = lint("lintV25");
        assertEquals(0, exitCode);
        assertLog(".*flows: 2.*");
    }

    @Test
    void lintV25RejectsMalformedTopLevelExpressionWithLocation() throws Exception {
        int exitCode = lint("lintV25InvalidTopLevel");

        assertEquals(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("ERROR V25_EXPRESSION at concord\\.yml:\\d+:\\d+-\\d+:\\d+ "
                + "\\(flows\\.default\\[0\\]\\.expr\\): Invalid expression");
    }

    @Test
    void lintV25RejectsMalformedNestedExpressionWithLocation() throws Exception {
        int exitCode = lint("lintV25InvalidNested");

        assertEquals(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("ERROR V25_EXPRESSION at concord\\.yml:\\d+:\\d+-\\d+:\\d+ "
                + "\\(flows\\.default\\[0\\]\\.in\\): Invalid expression");
    }

    @Test
    void lintV25AcceptsReorderedStepKeys() throws Exception {
        int exitCode = lint("lintV25Reordered");

        assertEquals(0, exitCode);
        assertLog(".*VALID.*");
    }

    @Test
    void lintV25RendersAllModelDiagnostics() throws Exception {
        int exitCode = lint("lintV25Diagnostics");
        assertEquals(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("(?s).*ERROR V25_STEP_OPTION at concord.yml:\\d+:\\d+-\\d+:\\d+.*suggestion: retry.*");
        assertErrContainsRegex("(?s).*ERROR V25_DEPRECATED_LOOP at concord.yml:\\d+:\\d+-\\d+:\\d+.*suggestion: loop.*");
    }

    @Test
    void lintV25RejectsUnknownLiteralCallTargetWithLocation() throws Exception {
        int exitCode = lint("lintV25UnknownCall");

        assertEquals(CliExitCodes.PROCESS_FAILED, exitCode);
        assertErrContainsRegex("ERROR V25_PLAN at concord\\.yml:\\d+:\\d+-\\d+:\\d+ "
                + "\\(flows\\.default\\[0\\]\\): Unknown flow 'missingFlow'");
    }

    @Test
    void lintV25ReportsNonFatalLoaderDiagnostics() throws Exception {
        int exitCode = lint("lintV25ImportWarning");

        assertEquals(CliExitCodes.SUCCESS, exitCode);
        assertLog(".*V25_EXTRA_IMPORTS_IGNORED: Imports declared outside the project root definition are ignored.*");
        assertLog(".*Result: 0 error\\(s\\), 1 warning\\(s\\).*");
    }

    private static int lint(String payload) throws Exception {
        URI uri = LintTest.class.getResource(payload).toURI();
        Path source = Paths.get(uri);

        try (TemporaryPath dst = PathUtils.tempDir("cli-tests")) {
            PathUtils.copy(source, dst.path());

            App app = new App();
            CommandLine cmd = new CommandLine(app);

            List<String> args = new ArrayList<>();
            args.add("lint");

            Path pwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
            Path relative = pwd.relativize(dst.path());

            args.add(relative.toString());

            return cmd.execute(args.toArray(new String[0]));
        }
    }
}
