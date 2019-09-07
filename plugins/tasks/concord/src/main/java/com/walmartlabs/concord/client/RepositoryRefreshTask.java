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

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Named("repositoryRefresh")
public class RepositoryRefreshTask extends AbstractConcordTask implements Task {

    @Override
    public void execute(Context ctx) throws Exception {
        List<Map<String, Object>> info = ContextUtils.getList(ctx, "repositoryInfo", Collections.emptyList());
        if (info.isEmpty()) {
            throw new RuntimeException("Invalid in parameters: no repository info");
        }

        List<UUID> ids = info.stream().map(v -> MapUtils.getUUID(v, "repositoryId")).collect(Collectors.toList());;

        withClient(ctx, client -> {
            RepositoriesV2Api api = new RepositoriesV2Api(client);
            api.refreshRepository(ids);
            return null;
        });
    }
}
