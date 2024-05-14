package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.repository.RepositoryRefresher;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/repository")
@Tag(name = "RepositoriesV2")
public class RepositoryResourceV2 implements Resource {

    private final RepositoryRefresher repositoryRefresher;

    @Inject
    public RepositoryResourceV2(RepositoryRefresher repositoryRefresher) {
        this.repositoryRefresher = repositoryRefresher;
    }

    /**
     * Refresh repositories by their IDs.
     */
    @POST
    @Path("/refresh")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Refresh repositories by their IDs", operationId = "refreshRepositoryV2")
    public GenericOperationResult refreshRepository(@QueryParam("ids") List<UUID> repositoryIds) {
        repositoryRefresher.refresh(repositoryIds);
        return new GenericOperationResult(OperationResult.UPDATED);
    }
}
