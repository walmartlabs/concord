package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.sdk.*;
import com.walmartlabs.concord.sdk.ContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.concurrent.Callable;

@Named
public class ObjectStorageImpl implements ObjectStorage {

    private static final Logger log = LoggerFactory.getLogger(LockServiceImpl.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiConfiguration cfg;
    private final ApiClientFactory apiClientFactory;

    @Inject
    public ObjectStorageImpl(ApiConfiguration cfg, ApiClientFactory apiClientFactory) {
        this.cfg = cfg;
        this.apiClientFactory = apiClientFactory;
    }

    @Override
    public BucketInfo createBucket(Context ctx, String name) throws Exception {
        ProjectInfo projectInfo = ContextUtils.getProjectInfo(ctx);
        if (projectInfo == null) {
            throw new IllegalArgumentException("project undefined");
        }

        InventoryEntry entry = new InventoryEntry();
        entry.setName(name);
        entry.setOrgId(projectInfo.orgId());
        entry.setVisibility(InventoryEntry.VisibilityEnum.PRIVATE);

        InventoriesApi api = new InventoriesApi(apiClientFactory.create(ctx));
        CreateInventoryResponse resp = withRetry(() -> api.createOrUpdate(projectInfo.orgName(), entry));
        log.info("createBucket ['{}', '{}'] -> done ({})", projectInfo.orgName(), name, resp.getId());

        String address = String.format("%s/api/v1/org/%s/inventory/%s/data/%s?singleItem=true",
                cfg.getBaseUrl(), projectInfo.orgName(), name, name);

        return ImmutableBucketInfo.builder()
                .address(address)
                .build();
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
