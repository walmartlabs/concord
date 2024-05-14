package com.walmartlabs.concord.it.server;//package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcessMetadataIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        // ---

        String randomStringA = "randomA_" + randomString();
        StartProcessResponse sprA = start(orgName, projectName, randomStringA);

        String randomStringB = "randomB_" + randomString();
        StartProcessResponse sprB = start(orgName, projectName, randomStringB);

        // ---

        waitForCompletion(getApiClient(), sprA.getInstanceId());
        waitForCompletion(getApiClient(), sprB.getInstanceId());

        // ---

        List<ProcessEntry> l = list(orgName, projectName, "meta.x", "123");
        assertEquals(2, l.size());

        l = list(orgName, projectName, "meta.x.startsWith", "123");
        assertEquals(2, l.size());

        l = list(orgName, projectName, "meta.y.endsWith", "abc");
        assertEquals(2, l.size());

        l = list(orgName, projectName, "meta.z.startsWith", "randomA");
        assertEquals(1, l.size());
        assertEquals(sprA.getInstanceId(), l.get(0).getInstanceId());

        l = list(orgName, projectName, "meta.z.startsWith", "randomB");
        assertEquals(1, l.size());
        assertEquals(sprB.getInstanceId(), l.get(0).getInstanceId());

        l = list(orgName, projectName, "meta.z.startsWith", "random");
        assertEquals(2, l.size());

        l = list(orgName, projectName, "meta.z.eq", randomStringA);
        assertEquals(1, l.size());
        assertEquals(sprA.getInstanceId(), l.get(0).getInstanceId());

        l = list(orgName, projectName, "meta.z.eq", randomStringB);
        assertEquals(1, l.size());
        assertEquals(sprB.getInstanceId(), l.get(0).getInstanceId());

        l = list(orgName, projectName, "meta.z.notEq", randomStringA);
        assertEquals(1, l.size());
        assertEquals(sprB.getInstanceId(), l.get(0).getInstanceId());
    }

    private StartProcessResponse start(String orgName, String projectName, String var) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("archive", archive(ProcessMetadataIT.class.getResource("processMetadata").toURI()));
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("arguments.myVar", var);
        return start(input);
    }

    private List<ProcessEntry> list(String orgName, String projectName, String... kv) throws Exception {
        Map<String, String> meta = new HashMap<>();

        for (int i = 0; i < kv.length - 1; i += 2) {
            meta.put(kv[i], kv[i + 1]);
        }

        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .meta(meta)
                .build();

        return new ProcessV2Api(getApiClient()).listProcesses(filter);
    }
}
