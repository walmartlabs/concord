package com.walmartlabs.concord.it.server;

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
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Execution(ExecutionMode.SAME_THREAD)
public class ProcessLocksIT extends AbstractServerIT {

    @Test
    public void testOrgScope() throws Exception {
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        byte[] payload = archive(ProcessLocksIT.class.getResource("processLocks").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        input.put("arguments.useForm", true);

        // start process A, it will acquire the lock and suspend on a form
        StartProcessResponse sprA = start(input);

        ProcessEntry pirA = waitForStatus(getApiClient(), sprA.getInstanceId(), StatusEnum.SUSPENDED);
        assertEquals(StatusEnum.SUSPENDED, pirA.getStatus());

        // verify it acquired the lock
        waitForLog(pirA.getInstanceId(), ".*locked!.*");

        // ---

        input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);

        // start process B, it should suspend waiting for the lock
        StartProcessResponse sprB = start(input);

        ProcessEntry pirB = waitForStatus(getApiClient(), sprB.getInstanceId(), StatusEnum.SUSPENDED);
        assertEquals(StatusEnum.SUSPENDED, pirB.getStatus());

        // ---

        // resume process A by submitting the form
        ProcessFormsApi formsApi = new ProcessFormsApi(getApiClient());
        List<FormListEntry> forms = formsApi.listProcessForms(sprA.getInstanceId());
        assertEquals(1, forms.size());

        Map<String, Object> formData = new HashMap<>();
        formData.put("x", "ok");
        formsApi.submitForm(sprA.getInstanceId(), forms.get(0).getName(), formData);

        // ---

        // wait for both processes to finish
        pirA = waitForStatus(getApiClient(), sprA.getInstanceId(), StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirA.getStatus());

        pirB = waitForStatus(getApiClient(), sprB.getInstanceId(), StatusEnum.FINISHED);
        assertEquals(StatusEnum.FINISHED, pirB.getStatus());
    }
}
