package com.walmartlabs.concord.server.agent;

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

import com.walmartlabs.concord.server.CommandType;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import java.util.HashMap;
import java.util.Map;

public final class Commands {

    public static final String TYPE_KEY = "type";

    public static final String INSTANCE_ID_KEY = "instanceId";

    public static Map<String, Object> cancel(ProcessKey processKey) {
        Map<String, Object> m = new HashMap<>();
        m.put(TYPE_KEY, CommandType.CANCEL_JOB);
        m.put(INSTANCE_ID_KEY, processKey.getInstanceId().toString());
        return m;
    }

}
