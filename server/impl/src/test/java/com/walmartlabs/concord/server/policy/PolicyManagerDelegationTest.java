package com.walmartlabs.concord.server.policy;

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

import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PolicyManagerDelegationTest {

    private final PolicyDao dao = mock(PolicyDao.class);
    private final PolicyCache cache = mock(PolicyCache.class);
    private final PolicyManager manager = new PolicyManager(cache, dao, mock(UserManager.class), TestObjectMapper.INSTANCE);

    @Test
    void invalidDelegationIsRejectedBeforeInsertOrUpdateAndCacheRefresh() {
        for (var raw : List.of(Map.of("allow", "not-a-list"), Map.of("allow", List.of(), "unknown", true))) {
            var rules = Map.<String, Object>of("externalIdentityDelegation", raw);
            assertThrows(ValidationErrorsException.class, () -> manager.insert("policy", null, rules));
            assertThrows(ValidationErrorsException.class, () -> manager.update(UUID.randomUUID(), "policy", null, rules));
        }
        var explicitNull = new HashMap<String, Object>();
        explicitNull.put("externalIdentityDelegation", null);
        assertThrows(ValidationErrorsException.class, () -> manager.insert("policy", null, explicitNull));
        assertThrows(ValidationErrorsException.class, () -> manager.update(UUID.randomUUID(), "policy", null, explicitNull));
        verifyNoInteractions(dao, cache);
    }

    @Test
    void validEmptyAndAbsentDelegationUseExistingPersistence() {
        for (var rules : List.of(Map.<String, Object>of(),
                Map.<String, Object>of("externalIdentityDelegation", Map.of("allow", List.of())))) {
            manager.insert("policy", null, rules);
            verify(dao).insert("policy", null, rules);
            clearInvocations(dao);
            var id = UUID.randomUUID();
            manager.update(id, "policy", null, rules);
            verify(dao).update(id, "policy", null, rules);
        }
        verify(cache, times(2)).refresh();
    }
}
