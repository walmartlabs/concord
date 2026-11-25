package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.POLICY_FILE_PATH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PolicyExportProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsPolicyWithoutWritingItToWorkspace() {
        Payload payload = new Payload(ProcessKey.random()).putHeader(Payload.WORKSPACE_DIR, tempDir);
        PolicyEngine policy = new PolicyEngine("test", PolicyEngineRules.builder().build());
        PolicyManager manager = mock(PolicyManager.class);
        when(manager.getPolicyEngine(payload)).thenReturn(policy);
        PolicyExportProcessor processor = new PolicyExportProcessor(manager, mock(ProcessLogManager.class));

        Payload result = processor.process(new Chain(), payload);

        assertSame(policy, result.getHeader(Payload.POLICY));
        assertFalse(Files.exists(tempDir.resolve(POLICY_FILE_PATH)));
    }

    @Test
    void continuesWithoutPolicy() {
        Payload payload = new Payload(ProcessKey.random());
        PolicyExportProcessor processor = new PolicyExportProcessor(mock(PolicyManager.class), mock(ProcessLogManager.class));

        assertSame(payload, processor.process(new Chain(), payload));
    }
}
