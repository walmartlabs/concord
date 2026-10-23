package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogTreeDao;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;

@Path("/api/v2/process/{id}/log/tree")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "ProcessLogV2")
public class ProcessLogTreeResource implements Resource {
    private final ProcessLogAccessManager access;
    private final ProcessLogTreeDao dao;

    @Inject
    public ProcessLogTreeResource(ProcessLogAccessManager access, ProcessLogTreeDao dao) {
        this.access = access;
        this.dao = dao;
    }

    @GET
    @WithTimer
    @Operation(operationId = "processLogTree", description = "Page roots or direct children of a segment")
    public LogSegmentTreePage page(@PathParam("id") UUID instanceId,
                                   @QueryParam("parentId") Long parentId,
                                   @QueryParam("afterId") Long afterId,
                                   @QueryParam("limit") @DefaultValue("100") int limit) {
        var key = access.assertLogAccess(instanceId);
        if (limit < 1 || limit > 100 || (parentId != null && parentId < 0) || (afterId != null && afterId < 0)) {
            throw new ValidationErrorsException("limit must be between 1 and 100; segment IDs must be nonnegative");
        }
        return dao.page(key, parentId, afterId, limit);
    }

    @GET
    @WithTimer
    @Path("search")
    @Operation(operationId = "processLogTreeSearch", description = "Search all process segments with ancestor context")
    public LogSegmentTreePage search(@PathParam("id") UUID instanceId,
                                     @QueryParam("afterId") Long afterId,
                                     @QueryParam("limit") @DefaultValue("100") int limit,
                                     @QueryParam("query") String query,
                                     @QueryParam("category") LogSegmentTreeNode.Category category,
                                     @QueryParam("errorsOnly") @DefaultValue("false") boolean errorsOnly,
                                     @QueryParam("reverse") @DefaultValue("false") boolean reverse) {
        var key = access.assertLogAccess(instanceId);
        if (limit < 1 || limit > 100 || (afterId != null && afterId < 0)) {
            throw new ValidationErrorsException("limit must be between 1 and 100; segment IDs must be nonnegative");
        }
        if (query != null && query.length() > 500) {
            throw new ValidationErrorsException("query must be at most 500 characters");
        }
        return dao.search(key, afterId, limit, query, category, errorsOnly, reverse);
    }

    @GET
    @WithTimer
    @Path("{segmentId}/descendants")
    @Operation(operationId = "processLogTreeDescendants", description = "Page descendants independently of expanded tree branches; ancestors are context only")
    public LogSegmentTreePage descendants(@PathParam("id") UUID instanceId,
                                          @PathParam("segmentId") long segmentId,
                                          @QueryParam("beforeId") Long beforeId,
                                          @QueryParam("afterId") Long afterId,
                                          @QueryParam("limit") @DefaultValue("25") int limit) {
        var key = access.assertLogAccess(instanceId);
        if (limit < 1 || limit > 100 || segmentId < 0 || (beforeId != null && beforeId < 0)
                || (afterId != null && afterId < 0) || (beforeId != null && afterId != null)) {
            throw new ValidationErrorsException("limit must be between 1 and 100; IDs must be nonnegative; use only one cursor");
        }
        var page = dao.descendants(key, segmentId, beforeId, afterId, limit);
        if (page.ancestors().stream().noneMatch(n -> n.segment().id() == segmentId)) {
            throw new NotFoundException("Log segment not found");
        }
        return page;
    }

    @GET
    @WithTimer
    @Path("nodes")
    @Operation(operationId = "processLogTreeNodes", description = "Refresh at most 100 loaded segments")
    public List<LogSegmentTreeNode> nodes(@PathParam("id") UUID instanceId, @QueryParam("segmentId") List<Long> segmentIds) {
        var key = access.assertLogAccess(instanceId);
        if (segmentIds.isEmpty() || segmentIds.size() > 100 || segmentIds.stream().anyMatch(id -> id == null || id < 0)) {
            throw new ValidationErrorsException("Supply between 1 and 100 nonnegative segment IDs");
        }
        return dao.nodes(key, segmentIds);
    }

    @GET
    @WithTimer
    @Path("{segmentId}/run")
    @Operation(operationId = "processLogTreeRun", description = "Resolve the execution segment used for step details")
    public LogSegment run(@PathParam("id") UUID instanceId, @PathParam("segmentId") long segmentId) {
        var key = access.assertLogAccess(instanceId);
        var result = dao.run(key, segmentId);
        if (result == null) {
            throw new NotFoundException("Log segment not found");
        }
        return result;
    }

    @GET
    @WithTimer
    @Path("{segmentId}/path")
    @Operation(operationId = "processLogTreePath", description = "Load a segment and its ancestors without loading siblings")
    public List<LogSegmentTreeNode> path(@PathParam("id") UUID instanceId, @PathParam("segmentId") long segmentId) {
        var key = access.assertLogAccess(instanceId);
        var result = dao.path(key, List.of(segmentId));
        if (result.isEmpty()) {
            throw new NotFoundException("Log segment not found");
        }
        return result;
    }
}
