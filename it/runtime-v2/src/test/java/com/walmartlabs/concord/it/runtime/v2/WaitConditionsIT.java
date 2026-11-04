package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.client2.ProcessEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;

public class WaitConditionsIT extends AbstractTest {

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration.configure();

    static ProcessApi processApi;

    @BeforeAll
    static void setup() {
        processApi = new ProcessApi(concord.apiClient());
    }

    @Test
    void single() throws Exception {
        var payload = new Payload()
                .entryPoint("single")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event", Map.of("externalResultOk", true));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*external result: true.*");
    }

    @Test
    void singleNullReturned() throws Exception {
        var payload = new Payload()
                .entryPoint("single")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event", null);

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*external result: null.*");
    }

    @Test
    void singleNoSaveAs() throws Exception {
        var payload = new Payload()
                .entryPoint("singleNoSaveAs")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event", Map.of("externalResultOk", true));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertNoLog(".*external result: true.*");
    }

    @Test
    void multipleEventsSingleThread() throws Exception {
        var payload = new Payload()
                .entryPoint("multipleEventsSingleThread")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_1", Map.of("externalResultOk", true));
        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_2", Map.of("aDifferentResultKey", Map.of("nested", true)));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*external result 1: true.*");
        proc.assertLog(".*external result 2: \\{nested=true}.*");
    }

    @Test
    void multipleEventsMultipleThreads() throws Exception {
        var payload = new Payload()
                .entryPoint("multipleEventsMultipleThreads")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_A_1", Map.of("externalResultOk", "result for A_1"));
        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_A_2", Map.of("aDifferentResultKey", "result for A_2"));
        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_B_1", Map.of("externalResultOk", "result for B_1"));
        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_B_2", Map.of("aDifferentResultKey", "result for B_2"));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*external result A_1: result for A_1.*");
        proc.assertLog(".*external result A_2: result for A_2.*");
        proc.assertLog(".*external result B_1: result for B_1.*");
        proc.assertLog(".*external result B_2: result for B_2.*");
    }

    @Test
    void expiration() throws Exception {
        var payload = new Payload()
                .entryPoint("expire")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // watchdog should notice expired condition and move forward

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertLog(".*External event wait condition for 'external_event' expired. 'saveAs' resume variable may not exist.*");
    }

    @Test
    void oneSuccessOneExpired() throws Exception {
        var payload = new Payload()
                .entryPoint("oneSuccessOneExpired")
                .arg("doIgnoreErrors", "${false}")
                .archive(resource("waitConditions"));

        var proc = concord.processes().start(payload);
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        processApi.clearExternalWaitCondition(proc.instanceId(), "external_event_1", Map.of("externalResultOk", true));

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);
        proc.assertNoLog(".*External event wait condition for 'external_event_1' expired. 'saveAs' resume variable may not exist.*");
        proc.assertLog(".*External event wait condition for 'external_event_2' expired. 'saveAs' resume variable may not exist.*");
        proc.assertLog(".*external result 1: true.*");
        proc.assertLog(".*external result 2: null.*");
    }

}
