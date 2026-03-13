package com.walmartlabs.concord.server.events;

import com.google.common.cache.LoadingCache;
import com.walmartlabs.concord.server.cfg.GithubConfiguration;
import com.walmartlabs.concord.server.events.GithubEventResource.GithubEventInitiatorSupplier;
import com.walmartlabs.concord.server.events.github.Payload;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

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

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager, payload, ghUserEmailCache);


        // when: get call finds user in fallback

        initiatorSupplier.get();


        // then: user found via fallback and user is added to cache

        verify(userManager, times(1)).getUserFromExternalMapping(anyString());
        verify(userManager, times(1)).getOrCreate("mock-login", null, UserType.LDAP);
        verify(userManager, times(1)).createExternalUserMapping(any(), any());
        verifyNoMoreInteractions(userManager);
    }

    @Test
    void userNotCreatedWhenFoundInMappingCache() {

        // given: ExternalUserIdMappingCache is enabled and user exists in the db mapping cache

        when(githubCfg.isEnableExternalUserIdMappingCache()).thenReturn(true);
        when(userManager.getUserFromExternalMapping(any())).thenReturn(Optional.of(userEntry));

        var initiatorSupplier = new GithubEventInitiatorSupplier(githubCfg, userManager, ldapManager, payload, ghUserEmailCache);


        // when: get call finds user in db mapping cache

        initiatorSupplier.get();


        // then: cache lookup occurred, no mapping is created

        verify(userManager, times(1)).getUserFromExternalMapping(any());
        verify(userManager, times(0)).createExternalUserMapping(any(), any());
        verifyNoMoreInteractions(userManager);
    }

}
