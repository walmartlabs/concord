package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.HttpUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import static com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;

/**
 * API to work with segmented process logs.
 */
@Path("/api/v2/process")
@Tag(name = "ProcessLogV2")
public class ProcessLogResourceV2 implements Resource {

    private final ProcessManager processManager;
    private final ProcessLogManager logManager;
    private final ProcessLogAccessManager logAccessManager;
    private final ProcessConfiguration processCfg;

    @Inject
    public ProcessLogResourceV2(ProcessManager processManager,
                                ProcessLogManager logManager,
                                ProcessLogAccessManager logAccessManager,
                                ProcessConfiguration processCfg) {
        this.processManager = processManager;
        this.logManager = logManager;
        this.logAccessManager = logAccessManager;
        this.processCfg = processCfg;
    }

    /**
     * List process log segments.
     */
    @GET
    @Path("{id}/log/segment")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List process log segments", operationId = "processLogSegments")
    public List<LogSegment> segments(@PathParam("id") UUID instanceId,
                                     @QueryParam("limit") @DefaultValue("30") int limit,
                                     @QueryParam("offset") @DefaultValue("0") int offset,
                                     @QueryParam("parentId") Long parentId,
                                     @QueryParam("rootOnly") @DefaultValue("false") boolean rootOnly,
                                     @QueryParam("collectErrors") @DefaultValue("false") boolean collectErrors,
                                     @QueryParam("onlyNamedSegments") @DefaultValue("false") boolean onlyNamedSegments) {

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        return logManager.listSegments(processKey, limit, offset, parentId, rootOnly, collectErrors, onlyNamedSegments);
    }

    /**
     * Create a new process log segment.
     */
    @POST
    @Path("{id}/log/segment")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Create process log segment", operationId = "createProcessLogSegment")
    public LogSegmentOperationResponse segment(@PathParam("id") UUID instanceId,
                                               LogSegmentRequest request) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        long segmentId = logManager.createSegment(processKey, request.correlationId(), request.name(), request.createdAt(), request.parentId(), request.meta());
        return new LogSegmentOperationResponse(segmentId, OperationResult.CREATED);
    }

    /**
     * Update a process log segment.
     */
    @POST
    @Path("{id}/log/segment/{segmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Update process log segment", operationId = "updateProcessLogSegment")
    public LogSegmentOperationResponse updateSegment(@PathParam("id") UUID instanceId,
                                                     @PathParam("segmentId") long segmentId,
                                                     LogSegmentUpdateRequest request) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        logManager.updateSegment(processKey, segmentId, request.status(), request.warnings(), request.errors());
        return new LogSegmentOperationResponse(segmentId, OperationResult.UPDATED);
    }

    /**
     * Retrieves a log segment' data.
     */
    @GET
    @Path("/{id}/log/segment/{segmentId}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Operation(description = "Retrieve segment the log", operationId = "getProcessLogSegmentData")
    @ApiResponse(description = "Data of process log segment",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response data(@PathParam("id") UUID instanceId,
                         @PathParam("segmentId") long segmentId,
                         @HeaderParam("range") String rangeHeader) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        HttpUtils.Range range = HttpUtils.parseRangeHeaderValue(rangeHeader);
        ProcessLog l = logManager.segmentData(processKey, segmentId, range.start(), range.end());
        return toResponse(instanceId, segmentId, l, range);
    }

    /**
     * Appends a process' log.
     */
    @POST
    @Path("{id}/log/segment/{segmentId}/data")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Operation(description = "Appends a process' log", operationId = "appendProcessLogSegment")
    @RequestBody(description = "Log content", required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    public void append(@PathParam("id") UUID instanceId,
                       @PathParam("segmentId") long segmentId,
                       InputStream data) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);

        try {
            byte[] ab = data.readAllBytes();
            int upper = logManager.log(processKey, segmentId, ab);

            int logSizeLimit = processCfg.getLogSizeLimit();
            if (upper >= logSizeLimit) {
                logManager.error(processKey, "Maximum log size reached: {}. Process cancelled.", logSizeLimit);
                processManager.kill(processKey);
            }
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while appending a log: " + e.getMessage());
        }
    }

    public static Response toResponse(UUID instanceId, long segmentId, ProcessLog l, HttpUtils.Range range) {
        List<ProcessLogChunk> data = l.getChunks();
        if (data.isEmpty()) {
            int actualStart = range.start() != null ? range.start() : 0;
            int actualEnd = range.end() != null ? range.end() : actualStart;
            return downloadableFile(instanceId, segmentId, null, actualStart, actualEnd, l.getSize());
        }

        ProcessLogChunk firstChunk = data.get(0);
        int actualStart = firstChunk.getStart();

        ProcessLogChunk lastChunk = data.get(data.size() - 1);
        int actualEnd = lastChunk.getStart() + lastChunk.getData().length;

        StreamingOutput out = output -> {
            for (ProcessLogChunk e : data) {
                output.write(e.getData());
            }
        };

        return downloadableFile(instanceId, segmentId, out, actualStart, actualEnd, l.getSize());
    }

    private static Response downloadableFile(UUID instanceId, long segmentId, StreamingOutput out, int start, int end, int size) {
        return (out != null ? Response.ok(out) : Response.ok())
                .header("Content-Range", "bytes " + start + "-" + end + "/" + size)
                .header("Content-Type", MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=\"" + instanceId + "_" + segmentId + ".log\"")
                .build();
    }
}
