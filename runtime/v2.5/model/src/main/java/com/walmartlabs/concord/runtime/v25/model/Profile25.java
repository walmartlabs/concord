package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Form;
import com.walmartlabs.concord.runtime.model.Profile;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record Profile25(Configuration25 configuration, Set<String> publicFlows,
                        Map<String, Flow25> flows, Map<String, Form> forms,
                        SourceRange sourceRange) implements Profile, Serializable {

    public Profile25 {
        publicFlows = Values.set(publicFlows);
        flows = Collections.unmodifiableMap(new LinkedHashMap<>(flows));
        forms = Collections.unmodifiableMap(new LinkedHashMap<>(forms));
    }
}
