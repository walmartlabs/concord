package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.sdk.MapUtils;

import java.util.Map;

public class TriggerRunAs {

    public static TriggerRunAs from(Map<String, Object> runAs) {
        if (runAs == null || runAs.isEmpty()) {
            return null;
        }

        return new TriggerRunAs(runAs);
    }

    private final Map<String, Object> params;

    private TriggerRunAs(Map<String, Object> params) {
        this.params = params;
    }

    public String secretName() {
        return MapUtils.assertString(params, "withSecret");
    }
}
