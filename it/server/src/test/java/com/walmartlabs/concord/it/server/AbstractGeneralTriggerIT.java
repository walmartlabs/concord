package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.ProcessListFilter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AbstractGeneralTriggerIT extends AbstractServerIT {

    protected Map<ProcessEntry.StatusEnum, ProcessEntry> waitProcesses(
            String orgName, String projectName, ProcessEntry.StatusEnum first, ProcessEntry.StatusEnum... more) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());

        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .build();

        List<ProcessEntry> processes;
        while (true) {
            processes = processApi.listProcesses(filter);

            if (processes.size() == 1 + (more != null ? more.length : 0)) {
                break;
            }
            Thread.sleep(1000);
        }

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = new HashMap<>();
        for (ProcessEntry p : processes) {
            ProcessEntry pir = waitForStatus(getApiClient(), p.getInstanceId(), first, more);
            ProcessEntry pe = ps.put(pir.getStatus(), pir);
            if (pe != null) {
                throw new RuntimeException("already got process with '" + pe.getStatus() + "' status, id: " + pe.getInstanceId());
            }
        }
        return ps;
    }

    protected void assertProcessLog(ProcessEntry pir, String log) throws Exception {
        assertNotNull(pir);
        byte[] ab = getLog(pir.getInstanceId());
        assertLog(log, ab);
    }

    protected List<TriggerEntry> waitForTriggers(String orgName, String projectName, String repoName, int expectedCount) throws Exception {
        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> l = triggerResource.listTriggers(orgName, projectName, repoName);
            if (l != null && l.size() == expectedCount) {
                return l;
            }

            Thread.sleep(1000);
        }
    }
}
