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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveYamlPolicyTest {

    @Test
    void testDefault() {
        var rule = ImmutableEffectiveYamlRule.builder().build();
        var policy = new EffectiveYamlPolicy(rule);

        assertTrue(policy.renderEffectiveYaml());
        assertFalse(policy.isTooLarge(Long.MAX_VALUE));
    }

    @Test
    void testDisabled() {
        var rule = ImmutableEffectiveYamlRule.builder()
                .renderEffectiveYaml(false)
                .build();
        var policy = new EffectiveYamlPolicy(rule);

        assertFalse(policy.renderEffectiveYaml());
        assertFalse(policy.isTooLarge(Long.MAX_VALUE));
    }

    @Test
    void testTooLarge() {
        var rule = ImmutableEffectiveYamlRule.builder()
                .maxSizeInBytes(100L)
                .build();
        var policy = new EffectiveYamlPolicy(rule);

        assertTrue(policy.renderEffectiveYaml());
        assertTrue(policy.isTooLarge(Long.MAX_VALUE));
    }

    @Test
    void testUnderLimit() {
        var rule = ImmutableEffectiveYamlRule.builder()
                .maxSizeInBytes(100L)
                .build();
        var policy = new EffectiveYamlPolicy(rule);

        assertTrue(policy.renderEffectiveYaml());
        assertFalse(policy.isTooLarge(99));
        assertTrue(policy.isTooLarge(Long.MAX_VALUE));
    }
}
