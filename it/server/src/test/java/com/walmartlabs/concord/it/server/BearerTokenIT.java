package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BearerTokenIT {

    @Test
    public void test() throws Exception {
        URL urlObj = new URL(ITConstants.SERVER_URL + "/api/v1/org?limit=1");
        HttpURLConnection httpCon = (HttpURLConnection) urlObj.openConnection();

        httpCon.setRequestProperty("Authorization", "Bearer " + ServerClient.DEFAULT_API_KEY);

        int responseCode = httpCon.getResponseCode();
        assertEquals(HttpURLConnection.HTTP_OK, responseCode);
    }
}
