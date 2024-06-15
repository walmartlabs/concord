package com.walmartlabs.concord.client2;

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

import com.walmartlabs.concord.client2.impl.auth.ApiKey;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.xml.bind.DatatypeConverter;
import java.util.UUID;

public class ProcessApiTest {

    @Test
    @Disabled
    public void testDecrypt() throws Exception {
        ApiClient apiClient = new DefaultApiClientFactory("http://localhost:8001").create();
        apiClient.setAuth(new ApiKey("cTFxMXExcTE="));

        ProjectsApi projectsApi = new ProjectsApi(apiClient);
        EncryptValueResponse encrypted = projectsApi.encrypt("org_1692633472807_3d32f7", "project_1692633472833_a1a531", "123qwe");

        String encryptedValue = encrypted.getData();
        System.out.println(">>>" + encryptedValue);
        byte[] input;

        try {
            input = DatatypeConverter.parseBase64Binary(encryptedValue);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid encrypted string value, please verify that it was specified/copied correctly: " + e.getMessage());
        }

        ProcessApi api = new ProcessApi(apiClient);
        UUID pid = UUID.fromString("f891d797-d97e-4724-b0ba-91d48efce6d8");
        System.out.println(">>>'" + new String(api.decryptString(pid, input)) + "'");
    }
}
