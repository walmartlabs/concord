package com.walmartlabs.concord.server.security.ldap;

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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.immutables.value.Value;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.naming.NamingException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class CachingLdapManager implements LdapManager {

    private final LdapManager delegate;

    private final LoadingCache<CacheKey, Optional<LdapPrincipal>> principalByName;
    private final LoadingCache<String, Optional<LdapPrincipal>> principalByDn;
    private final LoadingCache<String, Optional<LdapPrincipal>> principalByMail;

    public CachingLdapManager(Duration cacheDuration,
                              LdapManager delegate) {

        this.delegate = delegate;
        this.principalByName = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheDuration.toMillis(), TimeUnit.MILLISECONDS)
                .concurrencyLevel(32)
                .recordStats()
                .build(new CacheLoader<CacheKey, Optional<LdapPrincipal>>() {
                    @Override
                    public Optional<LdapPrincipal> load(@Nonnull CacheKey key) throws Exception {
                        return Optional.ofNullable(delegate.getPrincipal(key.userName(), key.domain()));
                    }
                });

        this.principalByDn = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheDuration.toMillis(), TimeUnit.MILLISECONDS)
                .concurrencyLevel(32)
                .recordStats()
                .build(new CacheLoader<String, Optional<LdapPrincipal>>() {
                    @Override
                    public Optional<LdapPrincipal> load(@Nonnull String key) throws Exception {
                        return Optional.ofNullable(delegate.getPrincipalByDn(key));
                    }
                });

        this.principalByMail = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheDuration.toMillis(), TimeUnit.MILLISECONDS)
                .concurrencyLevel(32)
                .recordStats()
                .build(new CacheLoader<String, Optional<LdapPrincipal>>() {
                    @Override
                    public Optional<LdapPrincipal> load(String key) throws Exception {
                        return Optional.ofNullable(delegate.getPrincipalByMail(key));
                    }
                });
    }

    @Override
    public List<LdapGroupSearchResult> searchGroups(String filter) throws NamingException {
        return delegate.searchGroups(filter);
    }

    @Override
    public Set<String> getGroups(String username, String domain) throws NamingException {
        return delegate.getGroups(username, domain);
    }

    @Override
    public LdapPrincipal getPrincipal(String username, String domain) throws Exception {
        return principalByName.get(CacheKey.of(username, domain)).orElse(null);
    }

    @Override
    public LdapPrincipal getPrincipalByDn(String dn) throws Exception {
        return principalByDn.get(dn).orElse(null);
    }

    @Override
    public LdapPrincipal getPrincipalByMail(String email) throws Exception {
        return principalByMail.get(email).orElse(null);
    }

    @Value.Immutable
    interface CacheKey {

        String userName();

        @Nullable
        String domain();

        static CacheKey of(String userName, String domain) {
            return ImmutableCacheKey.builder()
                    .userName(userName)
                    .domain(domain)
                    .build();
        }
    }
}
