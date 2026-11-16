package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.assertLog;
import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.assertLogExactMatch;

public class LoopOutputTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    void serialRetryWithSegments() throws Exception {
        retryCollectsSuccessfulOutputs("serial", true);
    }

    @Test
    void parallelRetryWithSegments() throws Exception {
        retryCollectsSuccessfulOutputs("parallel", true);
    }

    @Test
    void serialRetryWithoutSegments() throws Exception {
        retryCollectsSuccessfulOutputs("serial", false);
    }

    @Test
    void parallelRetryWithoutSegments() throws Exception {
        retryCollectsSuccessfulOutputs("parallel", false);
    }

    @Test
    void blockOutputWithSegments() throws Exception {
        existingOutputWithSegments("loopBlock", "result: [10, 20, 30]");
    }

    @Test
    void parallelTaskOutputWithSegments() throws Exception {
        existingOutputWithSegments("parallelLoopTask", "result: [10, 20, 30]");
    }

    @Test
    void callOutputExpressionsWithSegments() throws Exception {
        existingOutputWithSegments("flowCallOutExpression",
                "out as expression (array) with loop: [abc, abc]",
                "out as expression (map) with loop: [abc_0, abc_1]");
    }

    private void existingOutputWithSegments(String resource, String... expected) throws Exception {
        runtime.deploy(resource);
        runtime.save(ProcessConfiguration.builder().build());
        var log = runtime.run(RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder().segmentedLogs(true).build())
                .build());
        for (var value : expected) {
            assertLog(log, ".*" + Pattern.quote(value) + ".*");
        }
    }

    private void retryCollectsSuccessfulOutputs(String mode, boolean segmentedLogs) throws Exception {
        runtime.deploy("loopRetryOutput");
        runtime.save(ProcessConfiguration.builder().entryPoint(mode).build());

        var log = runtime.run(RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder().segmentedLogs(segmentedLogs).build())
                .build());

        assertLogExactMatch(log, 6, ".*ConditionallyFailTask: fail.*");
        assertLogExactMatch(log, 6, ".*ConditionallyFailTask: ok.*");

        // One successful result per item, including repeated items, with no failed-attempt values.
        assertLog(log, ".*" + Pattern.quote("list output: [10, 10, 20]") + ".*");
        assertLog(log, ".*" + Pattern.quote("mapped output: [10:1, 10:2, 20:0]") + ".*");
    }
}
