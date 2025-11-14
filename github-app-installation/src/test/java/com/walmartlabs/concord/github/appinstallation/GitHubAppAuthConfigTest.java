package com.walmartlabs.concord.github.appinstallation;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.cfg.MappingAuthConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubAppAuthConfigTest {

    @Test
    void testUrlPatternMissingNamedGroup() {
        var ex = assertThrows(IllegalArgumentException.class, () -> new GitHubAppAuthConfigNew(
                "https://api.github.com",
                "mock-client-id",
                "/not/used/in/test",
                null,
                MappingAuthConfig.assertBaseUrlPattern(".*")
        ));

        assertTrue(ex.getMessage().contains("The url pattern must contain the ?<baseUrl> named group"));
    }
}
