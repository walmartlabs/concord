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
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.util.ThreadContext;

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
    private LdapManager ldapManager;

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

        when(userManager.getOrCreate("mock-login", null, UserType.LDAP))
                .thenReturn(Optional.of(userEntry));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager, payload, ghUserEmailCache, () -> {});


        // when: get call finds user in fallback

        initiatorSupplier.get();


        // then: user found via fallback and user is added to cache

        verify(userManager, times(1)).getUserFromExternalMapping(anyString());
        verify(userManager, times(1)).getOrCreate("mock-login", null, UserType.LDAP);
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

        when(userManager.getOrCreate("mock-login", null, UserType.LDAP))
                .thenReturn(Optional.of(userEntry));

        var emailCache = CacheBuilder.newBuilder()
                .build(new GithubEventResource.EmailCacheLoader(githubCfg, new HashMap<>(), authTokenProvider, new ObjectMapper()));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager, payload, emailCache, () -> {});


        // when: get call fails to look up the sender's email

        var result = initiatorSupplier.get();


        // then: the error doesn't propagate, user is resolved via fallback

        assertEquals(userEntry, result);
        verify(userManager, times(1)).getOrCreate("mock-login", null, UserType.LDAP);
        verifyNoInteractions(ldapManager);
    }

    @Test
    void userNotCreatedWhenFoundInMappingCache() {

        // given: ExternalUserIdMappingCache is enabled and user exists in the db mapping cache

        when(githubCfg.isEnableExternalUserIdMappingCache()).thenReturn(true);
        when(userManager.getUserFromExternalMapping(any())).thenReturn(Optional.of(userEntry));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager, payload, ghUserEmailCache, () -> {});


        // when: get call finds user in db mapping cache

        initiatorSupplier.get();


        // then: cache lookup occurred, no mapping is created

        verify(userManager, times(1)).getUserFromExternalMapping(any());
        verify(userManager, times(0)).createExternalUserMapping(any(), any());
        verifyNoMoreInteractions(userManager);
    }

    @Test
    void externalIdentityFailurePrecedesUserCacheAndLdap() {
        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager,
                payload, ghUserEmailCache, () -> {
                    throw new ConcordApplicationException("Actor rejected");
                });

        assertThrows(ConcordApplicationException.class, initiatorSupplier::get);
        verifyNoInteractions(githubCfg, userManager, ldapManager, ghUserEmailCache);
    }

    @Test
    void ldapDnResolutionStillPrecedesEmailAndLogin() throws Exception {
        when(githubCfg.isUseSenderLdapDn()).thenReturn(true);
        var sender = mock(Payload.class);
        when(sender.getSenderLdapDn()).thenReturn("uid=human,dc=example");
        var principal = new LdapPrincipal("human", "example", null, null, null, null, Set.of(), Map.of());
        when(ldapManager.getPrincipalByDn("uid=human,dc=example")).thenReturn(principal);
        when(userManager.getOrCreate("human", "example", UserType.LDAP)).thenReturn(Optional.of(userEntry));
        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager,
                sender, ghUserEmailCache, () -> {});

        assertEquals(userEntry, initiatorSupplier.get());
        verify(userManager).getOrCreate("human", "example", UserType.LDAP);
        verifyNoMoreInteractions(userManager);
        verifyNoInteractions(ghUserEmailCache);
    }

    @Test
    void ldapEmailResolutionStillPrecedesLogin() throws Exception {
        when(githubCfg.isUseSenderEmail()).thenReturn(true);
        when(ghUserEmailCache.get(any())).thenReturn(Optional.of("human@example.com"));
        var principal = new LdapPrincipal("human", "example", null, null, null, null, Set.of(), Map.of());
        when(ldapManager.getPrincipalByMail("human@example.com")).thenReturn(principal);
        when(userManager.getOrCreate("human", "example", UserType.LDAP)).thenReturn(Optional.of(userEntry));
        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager,
                payload, ghUserEmailCache, () -> {});

        assertEquals(userEntry, initiatorSupplier.get());
        verify(userManager).getOrCreate("human", "example", UserType.LDAP);
        verifyNoMoreInteractions(userManager);
    }

    @Test
    void externalCacheEntryCannotAuthorizeExecutionWithoutResolver() {
        when(githubCfg.isEnableExternalUserIdMappingCache()).thenReturn(true);
        var external = new UserEntry(UUID.randomUUID(), "external", null, null, Set.of(),
                UserType.EXTERNAL, null, Set.of(), false, null, false);
        when(userManager.getUserFromExternalMapping(any())).thenReturn(Optional.of(external));
        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager,
                payload, ghUserEmailCache, () -> {});

        assertThrows(ConcordApplicationException.class, initiatorSupplier::get);
        verify(userManager).getUserFromExternalMapping("github_url=https://mock.local/mock-login,node_id=123");
        verifyNoMoreInteractions(userManager);
        verifyNoInteractions(ldapManager, ghUserEmailCache);
    }

    @Test
    void useInitiatorFalseDoesNotResolveActor() {
        var check = mock(Runnable.class);
        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager,
                payload, ghUserEmailCache, check);
        var trigger = mock(TriggerEntry.class);
        when(trigger.getCfg()).thenReturn(Map.of("useInitiator", false));
        var subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(new UserPrincipal("github", userEntry), "github"));
        ThreadContext.bind(subject);
        ThreadContext.bind(mock(org.apache.shiro.mgt.SecurityManager.class));
        try {
            var event = Event.builder().id("delivery").name("github").initiator(initiatorSupplier).build();
            assertEquals(userEntry, new TriggerEventInitiatorResolver().resolve(trigger, event));
            verifyNoInteractions(check, githubCfg, userManager, ldapManager, ghUserEmailCache);
        } finally {
            ThreadContext.unbindSubject();
            ThreadContext.unbindSecurityManager();
        }
    }

    @Test
    void useInitiatorTrueDoesNotFallBackAfterActorRejection() {
        var trigger = mock(TriggerEntry.class);
        when(trigger.getCfg()).thenReturn(Map.of("useInitiator", true));
        var event = Event.builder().id("delivery").name("github").initiator(() -> {
            throw new ConcordApplicationException("Actor rejected");
        }).build();

        assertThrows(ConcordApplicationException.class, () -> new TriggerEventInitiatorResolver().resolve(trigger, event));
    }

}
