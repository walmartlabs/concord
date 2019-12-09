package com.walmartlabs.concord.server;

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

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.agent.AgentWorkerEntry;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AgentWorkerUtils {

    public static Map<Object, Long> groupBy(Collection<AgentWorkerEntry> data, String[] path) {
        return data.stream()
                .map(e -> ConfigurationUtils.get(e.capabilities(), path))
                .map(e -> e != null ? e : "n/a")
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private AgentWorkerUtils() {
    }
}
