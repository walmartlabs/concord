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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.server.plugins.noderoster.dao.HostsDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@Named
public class HostManager {

    private final HostsDao dao;
    private final HostNormalizer hostNormalizer;

    private final Cache<String, Optional<UUID>> hostCache;

    @Inject
    public HostManager(HostsDao dao, HostNormalizer hostNormalizer) {
        this.dao = dao;
        this.hostNormalizer = hostNormalizer;

        this.hostCache = CacheBuilder.newBuilder()
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .build();
    }

    @WithTimer
    public UUID getId(UUID hostId, String host) {
        if (hostId != null) {
            return hostId;
        }

        try {
            return hostCache.get(host, new GetLoader(host))
                    .orElse(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @WithTimer
    public UUID getOrCreate(String host) {
        try {
            return hostCache.get(host, new GetOrCreateLoader(host))
                    .orElseThrow(() -> new RuntimeException("Can't find a host: " + host));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Host findHost(String host) {
        String normalizedHost = hostNormalizer.normalize(host);
        UUID hostId = dao.getId(normalizedHost);
        if (hostId != null) {
            return new Host(hostId, normalizedHost);
        }

        return new Host(null, normalizedHost);
    }

    private static class Host {

        private final UUID id;
        private final String normalizedHost;

        public Host(UUID id, String normalizedHost) {
            this.id = id;
            this.normalizedHost = normalizedHost;
        }
    }

    private final class GetLoader implements Callable<Optional<UUID>> {

        private final String host;

        private GetLoader(String host) {
            this.host = host;
        }

        @Override
        public Optional<UUID> call() {
            Host h = findHost(host);
            if (h.id == null) {
                return Optional.empty();
            }

            return Optional.of(h.id);
        }
    }

    private final class GetOrCreateLoader implements Callable<Optional<UUID>> {

        private final String host;

        private GetOrCreateLoader(String host) {
            this.host = host;
        }

        @Override
        public Optional<UUID> call() {
            Host h = findHost(host);
            if (h.id != null) {
                return Optional.of(h.id);
            }

            return Optional.of(dao.insert(h.normalizedHost));
        }
    }
}
