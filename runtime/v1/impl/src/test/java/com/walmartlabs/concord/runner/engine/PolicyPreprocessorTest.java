package com.walmartlabs.concord.runner.engine;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.runner.engine.el.InjectVariableELResolver;
import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.Variables;
import io.takari.bpm.el.DefaultExpressionManager;
import io.takari.bpm.el.ExpressionManager;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyPreprocessorTest {

    private static final String EXPECTED_PROCESSED_POLICY = "{\"ansible\":{\"allow\":[],\"warn\":[],\"deny\":[{\"msg\":\"Can't download artifacts without Gatekeeper\",\"action\":\"uri\",\"params\":[{\"values\":[\"a\",\"b\",\"c\"],\"name\":\"url\"}]}]}}";

    @Test
    public void testPreprocess() throws Exception {
        ExpressionManager expressionManager = new DefaultExpressionManager(
                new String[]{InternalConstants.Context.CONTEXT_KEY, InternalConstants.Context.EXECUTION_CONTEXT_KEY},
                new InjectVariableELResolver());

        Context ctx = new ConcordExecutionContextFactory.ConcordExecutionContext(null, expressionManager, new Variables(), null, null);
        ctx.setVariable("gatekeeperArtifacts", Arrays.asList("a", "b", "c"));

        Path workDir = Files.createTempDirectory("concord-test");
        Files.createDirectories(workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME));

        // -- read original policy
        String originalPolicy;
        try (InputStream is = PolicyPreprocessorTest.class.getResourceAsStream("/com/walmartlabs/concord/runner/policy.json")) {
            ObjectMapper om = new ObjectMapper();
            originalPolicy = om.writeValueAsString(om.readValue(is, Map.class));
        }

        // -- write tmp policy
        Path policyFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(InternalConstants.Files.POLICY_FILE_NAME);
        try (InputStream is = PolicyPreprocessorTest.class.getResourceAsStream("/com/walmartlabs/concord/runner/policy.json")) {
            Files.copy(is, policyFile, StandardCopyOption.REPLACE_EXISTING);
        }

        // ---
        PolicyPreprocessor pp = new PolicyPreprocessor(workDir);
        pp.preTask("ansible", null, ctx);

        String processedPolicy = new String(Files.readAllBytes(policyFile));
        assertEquals(EXPECTED_PROCESSED_POLICY, processedPolicy);

        // ---
        pp.postTask("ansible", null, ctx);
        String restoredPolicy = new String(Files.readAllBytes(policyFile));
        assertEquals(originalPolicy, restoredPolicy);
    }
}
