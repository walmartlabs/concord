package com.walmartlabs.concord.client;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.walmartlabs.concord.client.model.EventRepository;
import com.walmartlabs.concord.client.model.RefreshEvent;
import com.walmartlabs.concord.client2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RepositoryRefreshTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(RepositoryRefreshTaskCommon.class);

    private final RepositoriesV2Api apiV2;
    private final ObjectMapper mapper;

    public RepositoryRefreshTaskCommon(ApiClient client) {
        this.apiV2 = new RepositoriesV2Api(client);
        this.mapper = new ObjectMapper()
                .registerModule(new Jdk8Module()); // for Optional usage
    }

    public void execute(RepositoryRefreshTaskParams in) throws ApiException {
        Map<String, Object> rawEvent = in.event();
        if (!"push".equals(rawEvent.get("type"))) {
            // very odd, but glad we checked
            log.warn("Non-push event received: {}", rawEvent.get("type"));
            return;
        }

        RefreshEvent event = mapper.convertValue(rawEvent, RefreshEvent.class);

        if (event.payload().deleted()) {
            log.warn("Event ref was deleted. Skip refresh.");
            return;
        }

        List<UUID> repositoriesUUIDs = event.repositoryInfo().stream()
                .filter(EventRepository::enabled)
                // TODO validate: is always branch? event if configured for commit id? tag?
                .filter(repo -> event.branch().equals(repo.branch().orElse(null)))
                .map(EventRepository::repositoryId)
                .toList();

        refresh(repositoriesUUIDs);
    }

    void refresh(List<UUID> repositoriesUUIDs) throws ApiException {
        if (repositoriesUUIDs.isEmpty()) {
            log.warn("No applicable repository IDs. Skip refresh.");
            return;
        }

        log.info("Repository ids to refresh: {}", repositoriesUUIDs);
        apiV2.refreshRepositoryV2(repositoriesUUIDs);
        log.info("Repository refresh completed");
    }
}
