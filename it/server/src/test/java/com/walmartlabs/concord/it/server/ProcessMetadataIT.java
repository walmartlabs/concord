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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.ServerClient;
import com.walmartlabs.concord.it.common.ServerCompatModule;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProcessMetadataIT extends AbstractServerIT {

    private static final TypeReference<List<ProcessEntry>> LIST_OF_PROCESS_ENTRIES = new TypeReference<List<ProcessEntry>>() {
    };

    @Test
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
        // ---

        String randomStringA = "randomA_" + randomString();
        StartProcessResponse sprA = start(orgName, projectName, randomStringA);

        String randomStringB = "randomB_" + randomString();
        StartProcessResponse sprB = start(orgName, projectName, randomStringB);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        waitForCompletion(processApi, sprA.getInstanceId());
        waitForCompletion(processApi, sprB.getInstanceId());

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
        ApiClient apiClient = getApiClient();
        HttpUrl.Builder b = HttpUrl.parse(apiClient.getBasePath() + "/api/v2/process")
                .newBuilder()
                .addQueryParameter("orgName", orgName)
                .addQueryParameter("projectName", projectName);

        for (int i = 0; i < kv.length - 1; i += 2) {
            b.addQueryParameter(kv[i], kv[i + 1]);
        }

        Request req = new Request.Builder()
                .url(b.build())
                .header("Authorization", ServerClient.DEFAULT_API_KEY)
                .build();

        OkHttpClient httpClient = apiClient.getHttpClient();
        Response resp = null;
        try {
            resp = httpClient.newCall(req).execute();
            if (!resp.isSuccessful()) {
                throw new RuntimeException("Request failed: " + resp);
            }

            ObjectMapper om = new ObjectMapper();
            om.registerModule(new ServerCompatModule()); // to parse timestamps
            return om.readValue(resp.body().byteStream(), LIST_OF_PROCESS_ENTRIES);
        } finally {
            if (resp != null && resp.body() != null) {
                resp.body().close();
            }
        }
    }
}
