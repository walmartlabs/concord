package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.api.Variables;

import java.util.Map;

public final class ContextUtils {

    @SuppressWarnings("unchecked")
    public static String getSessionToken(Context ctx) {
        Map<String, Object> processInfo = (Map<String, Object>) ctx.getVariable(Constants.Request.PROCESS_INFO_KEY);
        if (processInfo == null) {
            throw new IllegalArgumentException("Can't find process info in the context: " + ctx.getVariableNames());
        }

        return assertSessionToken(processInfo);
    }

    @SuppressWarnings("unchecked")
    public static String getSessionToken(Variables variables) {
        Map<String, Object> processInfo = (Map<String, Object>) variables.getVariable(Constants.Request.PROCESS_INFO_KEY);
        if (processInfo == null) {
            throw new IllegalArgumentException("Can't find process info in the variables: " + variables.getVariableNames());
        }

        return assertSessionToken(processInfo);
    }

    private static String assertSessionToken(Map<String, Object> processInfo) {
        String result = (String) processInfo.get("sessionKey"); // TODO rename to sessionToken?
        if (result == null) {
            throw new IllegalArgumentException("Can't determine the 'sessionToken' value.");
        }

        return result;
    }

    private ContextUtils() {
    }
}
