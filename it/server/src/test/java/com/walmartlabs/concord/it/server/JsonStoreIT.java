package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.JsonStoreApi;
import com.walmartlabs.concord.client.JsonStoreRequest;
import com.walmartlabs.concord.client.OrganizationEntry;
import com.walmartlabs.concord.client.OrganizationsApi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class JsonStoreIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testValidationJsonStoreRequest() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        JsonStoreApi api = new JsonStoreApi(getApiClient());
        try {
            api.createOrUpdate(orgName, new JsonStoreRequest().setName("<script></script>"));
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
            assertEquals("[{\"id\":\"PARAMETER createOrUpdate.arg1.name\",\"message\":\"must match \\\"^[0-9a-zA-Z][0-9a-zA-Z_@.\\\\-~]{2,128}$\\\"\"}]", e.getResponseBody());
        }
    }
}
