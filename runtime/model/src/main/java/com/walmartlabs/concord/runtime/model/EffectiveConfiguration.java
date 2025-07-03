package com.walmartlabs.concord.runtime.model;

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

import com.walmartlabs.concord.common.ConfigurationUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class EffectiveConfiguration {

    public static Map<String, Object> getEffectiveConfiguration(ProcessDefinition project, Collection<String> activeProfiles) {
        Map<String, Object> cfg = asMap(project.configuration());
        Map<String, ? extends Profile> profiles = project.profiles();

        Map<String, Object> view = new LinkedHashMap<>(cfg != null ? cfg : Collections.emptyMap());
        if (profiles == null || activeProfiles == null) {
            return view;
        }

        for (String n : activeProfiles) {
            Profile p1 = profiles.get(n);
            if (p1 == null) {
                continue;
            }

            Map<String, Object> overlays = asMap(p1.configuration());
            if (overlays != null) {
                view = ConfigurationUtils.deepMerge(view, overlays);
            }
        }

        return view;
    }

    private static Map<String, Object> asMap(Configuration cfg) {
        if (cfg == null) {
            return null;
        }

        return cfg.asMap();
    }

    private EffectiveConfiguration() {
    }
}
