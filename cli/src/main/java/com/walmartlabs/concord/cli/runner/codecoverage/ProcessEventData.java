package com.walmartlabs.concord.cli.runner.codecoverage;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.sdk.MapUtils;

public final class ProcessEventData {

    public static String phase(ProcessEventEntry entry) {
        return MapUtils.getString(entry.getData(), "phase");
    }

    public static String fileName(ProcessEventEntry event) {
        return MapUtils.getString(event.getData(), "fileName");
    }

    public static Integer line(ProcessEventEntry event) {
        Number lineNum = MapUtils.get(event.getData(), "line", null, Number.class);
        if (lineNum == null) {
            return null;
        }
        return lineNum.intValue();
    }

    public static String flowCallName(ProcessEventEntry event) {
        String description = MapUtils.getString(event.getData(), "description");
        if (description == null) {
            return null;
        }
        if (description.startsWith("Flow call: ")) {
            return description.substring("Flow call: ".length());
        }

        return null;
    }

    public static String processDefinitionId(ProcessEventEntry event) {
        return MapUtils.getString(event.getData(), "processDefinitionId");
    }

    private ProcessEventData() {
    }
}
