package com.walmartlabs.concord.server.plugins.noderoster;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.server.plugins.noderoster.cfg.NodeRosterEventsConfiguration;
import com.walmartlabs.concord.server.plugins.noderoster.dao.HostsDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.UUID;

@Named
@Singleton
public class HostManager {

    private final HostsDao dao;
    private final HostNormalizer hostNormalizer;

    private final LoadingCache<String, Optional<UUID>> hostCache;

    @Inject
    public HostManager(HostsDao dao, HostNormalizer hostNormalizer, NodeRosterEventsConfiguration cfg) {
        this.dao = dao;
        this.hostNormalizer = hostNormalizer;

        this.hostCache = CacheBuilder.newBuilder()
                .expireAfterAccess(cfg.getHostCacheDuration())
                .maximumSize(cfg.getHostCacheSize())
                .recordStats()
                .build(new CacheLoader<>() {
                    @Override
                    public @Nonnull Optional<UUID> load(@Nonnull String host) {
                        UUID id = findHost(host);
                        return Optional.ofNullable(id);
                    }
                });
    }

    @WithTimer
    public UUID getId(UUID hostId, String host) {
        if (hostId != null) {
            return hostId;
        }

        try {
            return hostCache.get(host)
                    .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @WithTimer
    public UUID getOrCreate(String host) {
        try {
            Optional<UUID> knownHost = hostCache.get(host);

            if (knownHost.isPresent()) {
                return knownHost.get();
            }

            // insert in db and update cache
            UUID id = dao.insert(hostNormalizer.normalize(host));
            hostCache.put(host, Optional.of(id));

            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID findHost(String host) {
        String normalizedHost = hostNormalizer.normalize(host);
        return dao.getId(normalizedHost);
    }
}
