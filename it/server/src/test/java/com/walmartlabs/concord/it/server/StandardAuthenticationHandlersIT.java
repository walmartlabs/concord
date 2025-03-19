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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class StandardAuthenticationHandlersIT extends AbstractServerIT {

    @Test
    public void testBearerToken() throws Exception {
        URL urlObj = new URL(ITConstants.SERVER_URL + "/api/v1/org?limit=1");
        HttpURLConnection httpCon = (HttpURLConnection) urlObj.openConnection();

        httpCon.setRequestProperty("Authorization", "Bearer " + DEFAULT_API_KEY);

        int responseCode = httpCon.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }

    @Test
    public void testSessionTokenAsUsername() throws Exception {
        URI dir = SuspendIT.class.getResource("sessionTokenAsUsername").toURI();
        byte[] payload = archive(dir);

        // ---

        String targetUrl = getApiClient().getBaseUri() + "/api/v1/org?limit=1";
        StartProcessResponse spr = start(Map.of(
                "archive", payload,
                "arguments.targetUrl", targetUrl));

        // ---

        ProcessEntry pir = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
        assertLog(".*statusCode=200.*", getLog(pir.getInstanceId()));
    }
}
