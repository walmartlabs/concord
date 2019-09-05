package com.walmartlabs.concord.server.org.triggers;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;

import java.util.Collections;
import java.util.Map;

public final class TriggerUtils {

    public static String getEntryPoint(TriggerEntry t) {
        return MapUtils.getString(t.getCfg(), Constants.Request.ENTRY_POINT_KEY);
    }

    public static boolean isUseInitiator(TriggerEntry t) {
        return MapUtils.getBoolean(t.getCfg(), Constants.Trigger.USE_INITIATOR, false);
    }

    public static boolean isUseEventCommitId(TriggerEntry t) {
        return MapUtils.getBoolean(t.getCfg(), Constants.Trigger.USE_EVENT_COMMIT_ID, false);
    }

    public static Map<String, Object> getExclusive(TriggerEntry t) {
        return MapUtils.getMap(t.getCfg(), Constants.Request.EXCLUSIVE, Collections.emptyMap());
    }

    private TriggerUtils() {
    }
}
