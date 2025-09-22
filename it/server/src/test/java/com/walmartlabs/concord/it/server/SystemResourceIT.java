package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.SystemApi;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemResourceIT extends AbstractServerIT {

    private static final URI URI001 = URI.create("https://github001.local/owner/repo.git");
    private static final URI URI002 = URI.create("https://github002.local/owner/repo.git");

    @Test
    void testGetExternalToken() throws Exception {
        // user with externalTokenLookup role
        var userBName = "user_external_token_lookup_" + randomString();
        var externalTokenLookupUser = addUser(userBName, Set.of("externalTokenLookup"));

        // get system-provided token with externalTokenLookup role
        var systemApi = new SystemApi(getApiClientForKey(externalTokenLookupUser.apiKey()));
        var token = assertDoesNotThrow(() -> systemApi.getExternalToken(URI001));
        assertEquals("mock-token", token.getToken());
        assertNull(token.getUsername());

        // again, but from config that provides username
        token = assertDoesNotThrow(() -> systemApi.getExternalToken(URI002));
        assertEquals("mock-token", token.getToken());
        assertNotNull(token.getUsername());
        assertEquals("customUser", token.getUsername());
    }

    @Test
    void testGetExternalTokenNoPermission() throws Exception {
        // user with no roles
        var userAName = "user_basic_" + randomString();
        var noRolesUser = addUser(userAName, Set.of());

        // attempt to get system-provided token with insufficient privileges
        var systemApiNoPerm = new SystemApi(getApiClientForKey(noRolesUser.apiKey()));
        var ex1 = assertThrows(ApiException.class, () -> systemApiNoPerm.getExternalToken(URI001));
        assertEquals(403, ex1.getCode());
    }

}
