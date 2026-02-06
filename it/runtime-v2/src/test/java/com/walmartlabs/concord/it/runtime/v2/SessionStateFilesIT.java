package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.Processes;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.ZipUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ConcordConfiguration.getServerUrlForAgent;
import static org.junit.jupiter.api.Assertions.*;

public class SessionStateFilesIT extends AbstractTest {

    static ConcordRule concord;

    @BeforeAll
    static void setUp(ConcordRule rule) {
        concord = rule;
    }

    @Test
    public void testSessionFileAccess() throws Exception {
        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(concord.apiClient());
        CreateUserResponse user = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(concord.apiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                        .userId(user.getId()));

        // ---
        Payload payload = new Payload()
                .archive(resource("sessionFileAccess"))
                .arg("baseUrl", getServerUrlForAgent(concord));

        ApiClient client = concord.apiClient().setApiKey(cakr.getKey());
        ConcordProcess proc = new Processes(client)
                .start(payload);

        // ---
        expectStatus(proc, ProcessEntry.StatusEnum.SUSPENDED);

        // ---
        Assertions.assertEquals(username, proc.getEntry().getInitiator());

        ProcessApi processApiAdmin = new ProcessApi(concord.apiClient());
        ProcessApi processApiInitiator = new ProcessApi(client);

        String file = "sensitive_data.txt";

        // ---
        assertCantDownloadFile(processApiAdmin, proc.instanceId(), file);

        // ---
        assertCantDownloadFile(processApiInitiator, proc.instanceId(), file);

        FormSubmitResponse fsr = proc.submitForm("myForm", Collections.emptyMap());
        assertNull(fsr.getErrors());
        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---
        proc.assertLog(".*Secret: top-secret-value.*");
    }

    private static void assertCantDownloadFile(ProcessApi processApi, UUID instanceId, String file) throws Exception {
        try {
            processApi.downloadAttachment(instanceId, Constants.Files.JOB_SESSION_FILES_DIR_NAME + "/" + file);
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(403, e.getCode());
        }

        String fullFileName = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" + Constants.Files.JOB_SESSION_FILES_DIR_NAME + "/" + file;

        // ---
        try (InputStream state = processApi.downloadState(instanceId)) {
            assertNoFileInState(fullFileName, state);
        }

        // ---
        try {
            processApi.downloadStateFile(instanceId, fullFileName);
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(403, e.getCode());
        }
    }

    private static void assertNoFileInState(String file, InputStream state) throws IOException  {
        Path target = Files.createTempDirectory("state-unzip");
        ZipUtils.unzip(state, target, false, (sourceFile, dstFile) -> {
            assertNotEquals(file, target.relativize(dstFile).toString());
        });
    }
}
