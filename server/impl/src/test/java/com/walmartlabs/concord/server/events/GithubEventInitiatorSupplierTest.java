package com.walmartlabs.concord.server.events;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.events.GithubEventResource.GithubEventInitiatorSupplier;
import com.walmartlabs.concord.server.events.github.Payload;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubEventInitiatorSupplierTest {

    @Mock
    private static GithubConfiguration githubCfg;

    @Mock
    private UserManager userManager;

    @Mock
    private EventDirectoryResolver directories;

    @Mock
    private LoadingCache<GithubEventResource.EmailCacheKey, Optional<String>> ghUserEmailCache;

    @Mock
    private AuthTokenProvider authTokenProvider;

    private final UserEntry userEntry = new UserEntry(UUID.randomUUID(), "mock-login", null, null, Set.of(), UserType.LDAP, null, Set.of(), false, OffsetDateTime.now(), false);

    private final Payload payload = Payload.from("push", Map.of(
            "repository", Map.of(
                    "full_name", "mock-org/mock-repo",
                    "clone_url", "https://mock.local/mock-org/mock-repo.git"
            ),
            "sender", Map.of(
                    "login", "mock-login",
                    "node_id", "123",
                    "url", "https://mock.local/mock-login"
            )
    ));

    @Test
    void fallbackFoundAddedToMappingCache() throws ExecutionException {

        // given: enableExternalUserIdMappingCache is enabled

        when(githubCfg.isEnableExternalUserIdMappingCache()).thenReturn(true);
        when(githubCfg.isUseSenderEmail()).thenReturn(true);

        when(ghUserEmailCache.get(any(GithubEventResource.EmailCacheKey.class)))
                .thenReturn(Optional.empty());

        when(directories.find(null, "username", "mock-login"))
                .thenReturn(Optional.of(userEntry));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, directories, payload, ghUserEmailCache);


        // when: get call finds user in fallback

        initiatorSupplier.get();


        // then: user found via fallback and user is added to cache

        verify(userManager, times(1)).getUserFromExternalMapping(anyString());
        verify(directories).find(null, "username", "mock-login");
        verify(userManager, times(1)).createExternalUserMapping(any(), any());
        verifyNoMoreInteractions(userManager);
    }

    @Test
    void tokenErrorFallsBackToLogin() {

        // given: the auth token lookup fails (e.g. a transient GitHub App API error)

        when(githubCfg.isUseSenderEmail()).thenReturn(true);
        when(githubCfg.getHttpClientTimeout()).thenReturn(Duration.ofSeconds(1));

        when(authTokenProvider.getToken(any(), any()))
                .thenThrow(new RuntimeException("Error sending request"));

        when(directories.find(null, "username", "mock-login"))
                .thenReturn(Optional.of(userEntry));

        var emailCache = CacheBuilder.newBuilder()
                .build(new GithubEventResource.EmailCacheLoader(githubCfg, new HashMap<>(), authTokenProvider, new ObjectMapper()));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, directories, payload, emailCache);


        // when: get call fails to look up the sender's email

        var result = initiatorSupplier.get();


        // then: the error doesn't propagate, user is resolved via fallback

        assertEquals(userEntry, result);
        verify(directories).find(null, "username", "mock-login");
    }

    @Test
    void userNotCreatedWhenFoundInMappingCache() {

        // given: ExternalUserIdMappingCache is enabled and user exists in the db mapping cache

        when(githubCfg.isEnableExternalUserIdMappingCache()).thenReturn(true);
        when(userManager.getUserFromExternalMapping(any())).thenReturn(Optional.of(userEntry));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, directories, payload, ghUserEmailCache);


        // when: get call finds user in db mapping cache

        initiatorSupplier.get();


        // then: cache lookup occurred, no mapping is created

        verify(userManager, times(1)).getUserFromExternalMapping(any());
        verify(userManager, times(0)).createExternalUserMapping(any(), any());
        verifyNoMoreInteractions(userManager);
    }

    @Test
    void resolvesInDnEmailLoginOrder() throws Exception {
        Payload withDn = Payload.from("push", Map.of(
                "repository", Map.of("full_name", "mock-org/mock-repo", "clone_url", "https://mock.local/mock-org/mock-repo.git"),
                "sender", Map.of("login", "mock-login", "ldap_dn", "opaque-dn", "node_id", "123", "url", "https://mock.local/mock-login")));
        when(githubCfg.getUserDirectory()).thenReturn("custom");
        when(githubCfg.isUseSenderLdapDn()).thenReturn(true);
        when(githubCfg.isUseSenderEmail()).thenReturn(true);
        when(ghUserEmailCache.get(any())).thenReturn(Optional.of("person@example.org"));
        when(directories.find("custom", "distinguishedName", "opaque-dn")).thenReturn(Optional.empty());
        when(directories.find("custom", "email", "person@example.org")).thenReturn(Optional.empty());
        when(directories.find("custom", "username", "mock-login")).thenReturn(Optional.of(userEntry));

        assertEquals(userEntry, new GithubEventInitiatorSupplier(githubCfg, userManager, directories, withDn, ghUserEmailCache).get());
        var order = inOrder(directories);
        order.verify(directories).find("custom", "distinguishedName", "opaque-dn");
        order.verify(directories).find("custom", "email", "person@example.org");
        order.verify(directories).find("custom", "username", "mock-login");
    }

    @Test
    void unavailableDirectoryDoesNotFallBack() {
        when(githubCfg.getUserDirectory()).thenReturn("custom");
        when(directories.find("custom", "username", "mock-login"))
                .thenThrow(new ConcordApplicationException("directory unavailable", javax.ws.rs.core.Response.Status.SERVICE_UNAVAILABLE));
        var supplier = new GithubEventInitiatorSupplier(githubCfg, userManager, directories, payload, ghUserEmailCache);
        assertEquals(503, assertThrows(ConcordApplicationException.class, supplier::get).getResponse().getStatus());
        verifyNoInteractions(userManager);
    }

}
