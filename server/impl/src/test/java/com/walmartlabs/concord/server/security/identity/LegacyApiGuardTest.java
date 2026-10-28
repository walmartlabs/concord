package com.walmartlabs.concord.server.security.identity;

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

import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.legacy.LegacyApiGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LegacyApiGuardTest {
    @Test
    void legacyApisDefaultOnAndCanBeDisabled() {
        assertDoesNotThrow(() -> new LegacyApiGuard(ConfigFactory.empty()).check());
        LegacyApiGuard disabled = new LegacyApiGuard(ConfigFactory.parseString("security.legacy.enabled=false"));
        assertThrows(ValidationErrorsException.class, disabled::check);
    }
}
