package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.util.Map;

import static com.walmartlabs.concord.runner.ConfigurationUtils.getSystemProperty;

@Named
public class ApiConfigurationImpl implements ApiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApiConfigurationImpl.class);

    private static final String BASE_URL_KEY = "api.baseUrl";
    private static final String BASE_URL = getSystemProperty(BASE_URL_KEY, "http://localhost:8001");

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

     @SuppressWarnings("unchecked")
     public String getSessionToken(Context ctx) {
         Object txId = ctx.getVariable(Constants.Context.TX_ID_KEY);

         Map<String, Object> processInfo = (Map<String, Object>) ctx.getVariable(Constants.Request.PROCESS_INFO_KEY);
         if (processInfo == null) {
             log.warn("getSessionToken ['{}'] -> process info not found", txId);
             return null;
         }

         String s = (String) processInfo.get("sessionKey");
         if (s == null) {
             log.warn("getSessionToken ['{}'] -> sessionKey not found", txId);
             return null;
         }

         return s;
     }
}
