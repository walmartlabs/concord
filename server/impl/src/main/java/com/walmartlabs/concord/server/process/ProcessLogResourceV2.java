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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.HttpUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import io.swagger.v3.oas.annotations.Parameter;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
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
@Named
@Singleton
//@Api(value = "ProcessLogV2", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v2/process")
public class ProcessLogResourceV2 implements Resource {

    private final ProcessKeyCache processKeyCache;
    private final ProcessManager processManager;
    private final ProcessLogManager logManager;
    private final ProcessLogAccessManager logAccessManager;
    private final ProcessConfiguration processCfg;

    @Inject
    public ProcessLogResourceV2(ProcessKeyCache processKeyCache,
                                ProcessManager processManager,
                                ProcessLogManager logManager,
                                ProcessLogAccessManager logAccessManager,
                                ProcessConfiguration processCfg) {
        this.processKeyCache = processKeyCache;
        this.processManager = processManager;
        this.logManager = logManager;
        this.logAccessManager = logAccessManager;
        this.processCfg = processCfg;
    }

    /**
     * List process log segments.
     */
    @GET
//    @ApiOperation(value = "List process log segments")
    @Path("{id}/log/segment")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<LogSegment> segments(@Parameter @PathParam("id") UUID instanceId,
                                     @Parameter @QueryParam("limit") @DefaultValue("30") int limit,
                                     @Parameter @QueryParam("offset") @DefaultValue("0") int offset) {

        if (offset < 0) {
            throw new ValidationErrorsException("'offset' must be a positive number or zero");
        }

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        return logManager.listSegments(processKey, limit, offset);
    }

    /**
     * Create a new process log segment.
     */
    @POST
//    @ApiOperation(value = "Create process log segment")
    @Path("{id}/log/segment")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public LogSegmentOperationResponse segment(@Parameter @PathParam("id") UUID instanceId,
                                               @Parameter LogSegmentRequest request) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        long segmentId = logManager.createSegment(processKey, request.correlationId(), request.name(), request.createdAt());
        return new LogSegmentOperationResponse(segmentId, OperationResult.CREATED);
    }

    /**
     * Update a process log segment.
     */
    @POST
//    @ApiOperation(value = "Update process log segment")
    @Path("{id}/log/segment/{segmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public LogSegmentOperationResponse updateSegment(@Parameter @PathParam("id") UUID instanceId,
                                                     @Parameter @PathParam("segmentId") long segmentId,
                                                     @Parameter LogSegmentUpdateRequest request) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);
        logManager.updateSegment(processKey, segmentId, request.status(), request.warnings(), request.errors());
        return new LogSegmentOperationResponse(segmentId, OperationResult.UPDATED);
    }

    /**
     * Retrieves a log segment' data.
     */
    @GET
//    @ApiOperation(value = "Retrieve the log")
    @Path("/{id}/log/segment/{segmentId}/data")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    public Response data(@Parameter @PathParam("id") UUID instanceId,
                         @Parameter @PathParam("segmentId") long segmentId,
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
    public void append(@Parameter @PathParam("id") UUID instanceId,
                       @Parameter @PathParam("segmentId") long segmentId,
                       InputStream data) {

        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);

        try {
            byte[] ab = IOUtils.toByteArray(data);
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
