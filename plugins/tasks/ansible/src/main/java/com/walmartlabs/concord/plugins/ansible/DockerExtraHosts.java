package com.walmartlabs.concord.plugins.ansible;

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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class DockerExtraHosts {

    @SuppressWarnings("unchecked")
    public static Collection<String> getHosts(Map<String, Object> options) {
        if (options == null) {
            return Collections.emptyList();
        }

        Object o = options.get("hosts");
        if (o == null) {
            return Collections.emptyList();
        }

        if (o instanceof Collection) {
            return (Collection<String>) o;
        }

        throw new IllegalArgumentException("Unexpected 'hosts' type: " + o);
    }
}
