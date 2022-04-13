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

import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class JsonStoreTaskIT extends AbstractServerIT {

    @Test
    public void testStoreOperations() throws Exception {
        withOrg(orgName -> {
            withProject(orgName, projectName -> {
                byte[] payload = archive(ProcessIT.class.getResource("jsonStoreTaskStoreTest").toURI());

                Map<String, Object> input = new HashMap<>();
                input.put("archive", payload);
                input.put("org", orgName);
                input.put("project", projectName);
                input.put("arguments.storeName", "store_" + randomString());
                input.put("arguments.itemPath", "item_" + randomString());

                StartProcessResponse spr = start(input);

                ProcessApi processApi = new ProcessApi(getApiClient());
                ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

                // ---

                byte[] ab = getLog(pir.getLogFileName());
                assertLog(".*the store doesn't exist.*", ab);
                assertLog(".*the item doesn't exist.*", ab);
                assertLog(".*the store exists now.*", ab);
                assertLog(".*the item exists now.*", ab);
                assertLog(".*item: \\{test=123}.*", ab);
            });
        });
    }

    @Test
    public void testPutGetData() throws Exception {
        withOrg(orgName -> {
            withProject(orgName, projectName -> {
                withStore(orgName, storeName -> {

                    byte[] payload = archive(ProcessIT.class.getResource("jsonStoreTask").toURI());

                    Map<String, Object> input = new HashMap<>();
                    input.put("archive", payload);
                    input.put("org", orgName);
                    input.put("project", projectName);
                    input.put("arguments.storeName", storeName);

                    StartProcessResponse spr = start(input);

                    ProcessApi processApi = new ProcessApi(getApiClient());
                    ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());

                    // ---

                    byte[] ab = getLog(pir.getLogFileName());
                    assertLog(".*empty: $", ab);
                    assertLog(".*get: \\{x=1}*", ab);
                });
            });
        });

    }

    private void withOrg(Consumer<String> consumer) throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        try {
            orgApi.createOrUpdate(new OrganizationEntry().setName(orgName));
            consumer.accept(orgName);
        } finally {
            orgApi.delete(orgName, "yes");
        }
    }

    private void withProject(String orgName, Consumer<String> consumer) throws Exception {
        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        try {
            projectsApi.createOrUpdate(orgName, new ProjectEntry()
                    .setName(projectName)
                    .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
            consumer.accept(projectName);
        } finally {
            projectsApi.delete(orgName, projectName);
        }
    }

    private void withStore(String orgName, Consumer<String> consumer) throws Exception {
        String storageName = "storage_" + randomString();
        JsonStoreApi storageApi = new JsonStoreApi(getApiClient());
        try {
            storageApi.createOrUpdate(orgName, new JsonStoreRequest()
                    .setName(storageName)
                    .setVisibility(JsonStoreRequest.VisibilityEnum.PUBLIC));
            consumer.accept(storageName);
        } finally {
            storageApi.delete(orgName, storageName);
        }
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t) throws Exception;
    }
}
