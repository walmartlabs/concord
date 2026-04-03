package com.walmartlabs.concord.policyengine;

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

public class EffectiveYamlPolicy {

    private final EffectiveYamlRule rule;

    public EffectiveYamlPolicy(EffectiveYamlRule rule) {
        this.rule = rule;
    }

    public boolean renderEffectiveYaml() {
        // enabled by default
        return rule == null || rule.renderEffectiveYaml();
    }

    public boolean isTooLarge(long sizeInBytes) {
        // no enforcement by default
        if (rule == null) {
            return false;
        }

        var maxSizeInBytes = rule.maxSizeInBytes();

        if (maxSizeInBytes == null) {
            return false;
        }

        return sizeInBytes > maxSizeInBytes;
    }

    public boolean logWarning() {
        // enabled by default
        return rule == null || rule.logWarning();
    }
}
