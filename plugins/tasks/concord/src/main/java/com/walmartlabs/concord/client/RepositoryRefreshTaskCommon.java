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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class RepositoryRefreshTaskCommon {

    private final RepositoriesV2Api api;

    private static final Logger log = LoggerFactory.getLogger(RepositoryRefreshTaskCommon.class);

    public RepositoryRefreshTaskCommon(ApiClient client) {
        this.api = new RepositoriesV2Api(client);
    }

    public void execute(RepositoryRefreshTaskParams in) throws ApiException {
        List<UUID> repositoriesUUIDs = in.repositories();
        log.info("Repository ids to refresh: {}",repositoriesUUIDs);
        api.refreshRepositoryV2(repositoriesUUIDs);
        log.info("Repository refresh completed");
    }
}
