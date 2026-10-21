package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.logger.LogSegmentStatus;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

public class LogLifecycleTest {
    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    void formSuspendsItsOwningCall() throws Exception {
        deploy("form");
        var log = run();
        long parent = runtime.testLoggingClient().getSegmentId("parent");
        assertEquals(List.of(LogSegmentStatus.SUSPENDED), statuses(log, parent));
        StateManager.saveResumeEvent(runtime.workDir(), runtime.formService().list().get(0).eventName());
        assertEquals(List.of(LogSegmentStatus.RUNNING, LogSegmentStatus.OK), statuses(run(), parent));
    }

    @Test
    void parallelResumeLeavesOtherBranchSuspended() throws Exception {
        deploy("parallel");
        var log = run();
        var client = runtime.testLoggingClient();
        long parent = client.getSegmentId("parent");
        long first = client.getSegmentId("first");
        long second = client.getSegmentId("second");
        assertTrue(statuses(log, parent).contains(LogSegmentStatus.SUSPENDED));
        assertTrue(statuses(log, first).contains(LogSegmentStatus.SUSPENDED));
        assertTrue(statuses(log, second).contains(LogSegmentStatus.SUSPENDED));

        StateManager.saveResumeEvent(runtime.workDir(), "first");
        log = run();
        assertEquals(List.of(LogSegmentStatus.RUNNING, LogSegmentStatus.SUSPENDED), statuses(log, parent));
        assertEquals(LogSegmentStatus.OK, statuses(log, first).getLast());
        assertFalse(statuses(log, second).contains(LogSegmentStatus.RUNNING));
        assertTrue(statuses(log, second).contains(LogSegmentStatus.SUSPENDED));

        StateManager.saveResumeEvent(runtime.workDir(), "second");
        log = run();
        assertEquals(List.of(LogSegmentStatus.RUNNING, LogSegmentStatus.OK), statuses(log, parent));
        assertEquals(LogSegmentStatus.OK, statuses(log, second).getLast());
    }

    @Test
    void checkpointReopensExistingParentAndCreatesNewChild() throws Exception {
        deploy("checkpoint");
        run();
        var client = runtime.testLoggingClient();
        long parent = client.getSegmentId("parent");
        long oldChild = client.getSegmentId("after checkpoint");
        runtime.checkpointService().restore("saved", runtime.workDir());
        var log = run();
        assertEquals(List.of(LogSegmentStatus.RUNNING, LogSegmentStatus.OK), statuses(log, parent));
        assertEquals(List.of(parent), client.getSegmentIds("parent"));
        var children = client.getSegmentIds("after checkpoint");
        assertEquals(2, children.size());
        assertEquals(oldChild, children.get(0));
        children.forEach(id -> assertEquals(parent, client.getSegmentParentId(id)));
    }

    @Test
    void checkpointReopensFailedParent() throws Exception {
        deploy("checkpoint");
        runtime.save(ProcessConfiguration.builder().putArguments("fail", true).build());
        assertThrows(Exception.class, this::run);
        long parent = runtime.testLoggingClient().getSegmentId("parent");
        assertEquals(LogSegmentStatus.ERROR, statuses(runtime.lastLog(), parent).getLast());

        runtime.checkpointService().restore("saved", runtime.workDir());
        runtime.save(ProcessConfiguration.builder().putArguments("fail", false).build());
        assertEquals(List.of(LogSegmentStatus.RUNNING, LogSegmentStatus.OK), statuses(run(), parent));
    }

    private void deploy(String scenario) throws Exception {
        runtime.deploy("logLifecycle/" + scenario);
        runtime.save(ProcessConfiguration.builder().putArguments("fail", false).build());
    }

    private byte[] run() throws Exception {
        return runtime.run(RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder().segmentedLogs(true).build()).build());
    }

    private static List<LogSegmentStatus> statuses(byte[] log, long id) {
        var matcher = Pattern.compile("\\|(?:0|13)\\|" + id + "\\|([0-3])\\|[0-9]+\\|[0-9]+\\|")
                .matcher(new String(log, StandardCharsets.UTF_8));
        var result = new ArrayList<LogSegmentStatus>();
        while (matcher.find()) result.add(LogSegmentStatus.fromId(Integer.parseInt(matcher.group(1))));
        return result;
    }
}
