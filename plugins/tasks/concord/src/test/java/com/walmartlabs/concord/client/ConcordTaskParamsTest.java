package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client.ConcordTaskParams.ForkParams;
import com.walmartlabs.concord.client.ConcordTaskParams.ForkStartParams;
import com.walmartlabs.concord.client.ConcordTaskParams.CreateOrUpdateApiKeyParams;
import com.walmartlabs.concord.client2.CreateApiKeyRequest;
import com.walmartlabs.concord.client2.ExternalIdentityKey;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConcordTaskParamsTest {

    @Test
    public void testForks() {
        List<String> tags = Arrays.asList("x", "y", "z");

        Map<String, Object> input = new HashMap<>();
        input.put("action", "fork");
        input.put("tags", tags);
        input.put("forks", Arrays.asList(
                Collections.singletonMap("entryPoint", "aaa"),
                Collections.singletonMap("entryPoint", "bbb")
        ));

        ForkParams params = (ForkParams) ConcordTaskParams.of(new MapBackedVariables(input), Collections.emptyMap());

        List<ForkStartParams> forks = params.forks();
        assertEquals(2, forks.size());

        ForkStartParams f1 = forks.get(0);
        assertEquals("aaa", f1.entryPoint());
        assertTrue(f1.tags().containsAll(tags));

        ForkStartParams f2 = forks.get(1);
        assertEquals("bbb", f2.entryPoint());
        assertTrue(f2.tags().containsAll(tags));

        System.out.println(params);
    }

    @Test
    public void testApiKeyIdentitySelector() {
        Map<String, Object> identity = Map.of("providerId", "custom", "authority", "", "subject", "CaseSensitive");
        CreateOrUpdateApiKeyParams params = new CreateOrUpdateApiKeyParams(new MapBackedVariables(Map.of("identity", identity)));
        ExternalIdentityKey key = params.identity();
        assertEquals("custom", key.getProviderId());
        assertEquals("", key.getAuthority());
        assertEquals("CaseSensitive", key.getSubject());
        assertNull(params.userId());

        for (String field : List.of("userId", "username", "userDomain", "userType")) {
            Map<String, Object> input = new HashMap<>();
            input.put("identity", identity);
            input.put(field, field.equals("userId") ? UUID.randomUUID().toString() : "value");
            assertThrows(IllegalArgumentException.class,
                    () -> new CreateOrUpdateApiKeyParams(new MapBackedVariables(input)).identity());
        }
        assertThrows(IllegalArgumentException.class, () -> new CreateOrUpdateApiKeyParams(
                new MapBackedVariables(Map.of("identity", Map.of("providerId", "p", "subject", "s")))).identity());
    }

    @Test
    public void testApiKeyLegacySelectors() {
        UUID userId = UUID.randomUUID();
        CreateOrUpdateApiKeyParams params = new CreateOrUpdateApiKeyParams(new MapBackedVariables(Map.of(
                "userId", userId.toString(), "userDomain", "example", "userType", "sso")));
        assertNull(params.identity());
        assertEquals(userId, params.userId());
        assertEquals(CreateApiKeyRequest.UserTypeEnum.SSO, params.userType());
        assertThrows(IllegalArgumentException.class, () -> new CreateOrUpdateApiKeyParams(
                new MapBackedVariables(Map.of("userType", "oidc"))).userType());
    }
}
