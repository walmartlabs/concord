package com.walmartlabs.concord.policyengine;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkspacePolicyTest {

    @Test
    public void testMaxSize() throws Exception {
        Path p = Files.createTempDirectory("test1");
        Files.write(p.resolve("test.bin"), new byte[]{0, 1, 2, 3, 4, 5}, StandardOpenOption.CREATE_NEW);

        WorkspacePolicy fiveBytes = new WorkspacePolicy(WorkspaceRule.of("5 bytes", 5L, null));
        WorkspacePolicy tenBytes = new WorkspacePolicy(WorkspaceRule.of("10 bytes", 10L, null));

        // ---

        assertDeny(fiveBytes, p);
        assertAllow(tenBytes, p);
    }

    private static void assertAllow(WorkspacePolicy policy, Path p) throws IOException {
        CheckResult<WorkspaceRule, Path> result = policy.check(p);
        assertTrue(result.getDeny().isEmpty());
    }

    private static void assertDeny(WorkspacePolicy policy, Path p) throws IOException {
        CheckResult<WorkspaceRule, Path> result = policy.check(p);
        assertFalse(result.getDeny().isEmpty());
    }
}
