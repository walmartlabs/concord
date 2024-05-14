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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.policyengine.AttachmentsRule;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.HttpUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.OffsetDateTimeParam;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.events.ExpressionUtils;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.policy.PolicyException;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.PayloadManager.EntryPoint;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessStatusHistoryEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessWaitEntry;
import com.walmartlabs.concord.server.process.ProcessManager.ProcessResult;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.waits.AbstractWaitCondition;
import com.walmartlabs.concord.server.process.waits.ProcessWaitManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@javax.ws.rs.Path("/api/v1/process")
@Tag(name = "Process")
public class ProcessResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResource.class);

    private final ProcessWaitManager processWaitManager;
    private final ProcessManager processManager;
    private final ProcessQueueDao queueDao;
    private final ProcessQueueManager processQueueManager;
    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final SecretStoreConfiguration secretStoreCfg;
    private final EncryptedProjectValueManager encryptedValueManager;
    private final ProcessKeyCache processKeyCache;
    private final ObjectMapper objectMapper;
    private final ProjectAccessManager projectAccessManager;
    private final ProcessConfiguration processCfg;
    private final ProcessLogManager logManager;
    private final ProcessLogAccessManager logAccessManager;
    private final ProcessLogManager processLogManager;
    private final PolicyManager policyManager;

    private final ProcessResourceV2 v2;

    @Inject
    public ProcessResource(ProcessWaitManager processWaitManager,
                           ProcessManager processManager,
                           ProcessQueueDao queueDao,
                           ProcessQueueManager processQueueManager,
                           PayloadManager payloadManager,
                           ProcessStateManager stateManager,
                           SecretStoreConfiguration secretStoreCfg,
                           EncryptedProjectValueManager encryptedValueManager,
                           ProjectAccessManager projectAccessManager,
                           ProcessKeyCache processKeyCache,
                           ObjectMapper objectMapper,
                           ProcessConfiguration processCfg,
                           ProcessLogManager logManager,
                           ProcessLogAccessManager logAccessManager,
                           ProcessLogManager processLogManager,
                           PolicyManager policyManager,
                           ProcessResourceV2 v2) {

        this.processWaitManager = processWaitManager;
        this.processManager = processManager;
        this.queueDao = queueDao;
        this.processQueueManager = processQueueManager;
        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.secretStoreCfg = secretStoreCfg;
        this.encryptedValueManager = encryptedValueManager;
        this.projectAccessManager = projectAccessManager;
        this.processKeyCache = processKeyCache;
        this.objectMapper = objectMapper;
        this.processCfg = processCfg;
        this.logManager = logManager;
        this.logAccessManager = logAccessManager;
        this.processLogManager = processLogManager;
        this.policyManager = policyManager;

        this.v2 = v2;
    }

    /**
     * Starts a new process instance.
     *
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[], HttpServletRequest)}
     */
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_octetstream")
    @Deprecated
    public StartProcessResponse start(InputStream in,
                                      @QueryParam("parentId") UUID parentInstanceId,
                                      @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @QueryParam("out") String[] out) {

        if (sync) {
            throw syncIsForbidden();
        }

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), in, out);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload));
    }

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[], HttpServletRequest)}
     */
    @POST
    @javax.ws.rs.Path("/{entryPoint}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_queryparams")
    @Deprecated
    public StartProcessResponse start(@PathParam("entryPoint") String entryPoint,
                                      @QueryParam("parentId") UUID parentInstanceId,
                                      @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @QueryParam("out") String[] out) {

        return start(entryPoint, Collections.emptyMap(), parentInstanceId, sync, out);
    }

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[], HttpServletRequest)}
     */
    @POST
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_json")
    @Deprecated
    public StartProcessResponse start(@PathParam("entryPoint") String entryPoint,
                                      Map<String, Object> req,
                                      @QueryParam("parentId") UUID parentInstanceId,
                                      @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @QueryParam("out") String[] out) {

        if (sync) {
            throw syncIsForbidden();
        }

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(processKey, orgId, entryPoint);
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), ep, req, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload));
    }

    /**
     * Starts a new process instance.
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Start new process", operationId = "startProcess")
    public StartProcessResponse start(@Parameter(schema = @Schema(type = "object", implementation = Object.class)) MultipartInput input,
                                      @Parameter(hidden = true) @Deprecated @QueryParam("parentId") UUID parentInstanceId,
                                      @Parameter(hidden = true) @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @Parameter(hidden = true) @Deprecated @QueryParam("out") String[] out,
                                      @Context HttpServletRequest request) {

        boolean sync2 = MultipartUtils.getBoolean(input, Constants.Multipart.SYNC, false);
        if (sync || sync2) {
            throw syncIsForbidden();
        }

        Payload payload;
        try {
            payload = payloadManager.createPayload(input, request);

            // TODO remove after deprecating the old endpoints
            payload = PayloadBuilder.basedOn(payload)
                    .parentInstanceId(parentInstanceId)
                    .mergeOutExpressions(out)
                    .build();
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload));
    }

    /**
     * Starts a new process instance using the specified entry point and multipart request data.
     *
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[], HttpServletRequest)}
     */
    @POST
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_with_entrypoint")
    @Deprecated
    public StartProcessResponse start(@PathParam("entryPoint") String entryPoint,
                                      MultipartInput input,
                                      @QueryParam("parentId") UUID parentInstanceId,
                                      @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @QueryParam("out") String[] out) {

        if (sync) {
            throw syncIsForbidden();
        }

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(processKey, orgId, entryPoint);
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), ep, input, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload));
    }

    /**
     * Starts a new process instance using the specified entry point and payload archive.
     *
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[], HttpServletRequest)}
     */
    @POST
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_octetstream_and_entrypoint")
    @Deprecated
    public StartProcessResponse start(@PathParam("entryPoint") String entryPoint,
                                      InputStream in,
                                      @QueryParam("parentId") UUID parentInstanceId,
                                      @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @QueryParam("out") String[] out) {

        if (sync) {
            throw syncIsForbidden();
        }

        // allow empty POST requests
        if (isEmpty(in)) {
            return start(entryPoint, parentInstanceId, sync, out);
        }

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(processKey, orgId, entryPoint);
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), ep, in, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload));
    }

    @POST
    @javax.ws.rs.Path("/{id}/restart")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Restart process", operationId = "restartProcess")
    public StartProcessResponse restart(@PathParam("id") UUID instanceId) {
        ProcessKey processKey = assertProcessKey(instanceId);
        processManager.restart(processKey);
        return new StartProcessResponse(instanceId);
    }

    /**
     * Resumes an existing process.
     */
    @POST
    @javax.ws.rs.Path("/{id}/resume/{eventName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation
    public ResumeProcessResponse resume(@PathParam("id") UUID instanceId,
                                        @PathParam("eventName") @NotNull String eventName,
                                        @QueryParam("saveAs") String saveAs,
                                        Map<String, Object> req) {

        ProcessKey processKey = assertProcessKey(instanceId);

        processManager.assertResumeEvents(processKey, Set.of(eventName));

        if (saveAs != null && !saveAs.isEmpty() && req != null) {
            req = ConfigurationUtils.toNested(saveAs, req);
        }

        req = ExpressionUtils.escapeMap(req);

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            log.error("resume ['{}', '{}'] -> error creating a payload: {}", instanceId, eventName, e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        processManager.resume(payload);
        return new ResumeProcessResponse();
    }

    /**
     * Starts a new child process by forking the start of the specified parent process.
     */
    @POST
    @javax.ws.rs.Path("/{id}/fork")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Starts a new child process by forking the start of the specified parent process.")
    public StartProcessResponse fork(@PathParam("id") UUID parentInstanceId,
                                     Map<String, Object> req,
                                     @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                     @QueryParam("out") String[] out) {

        if (sync) {
            throw syncIsForbidden();
        }

        ProcessEntry parent = processQueueManager.get(PartialProcessKey.from(parentInstanceId));
        if (parent == null) {
            throw new ValidationErrorsException("Unknown parent instance ID: " + parentInstanceId);
        }

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());
        ProcessKey parentProcessKey = new ProcessKey(parent.instanceId(), parent.createdAt());

        UUID projectId = parent.projectId();
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();
        Set<String> handlers = parent.handlers();
        Imports imports = queueDao.getImports(parentProcessKey);

        Payload payload;
        try {
            payload = payloadManager.createFork(processKey, parentProcessKey, ProcessKind.DEFAULT,
                    userPrincipal.getId(), userPrincipal.getUsername(), projectId, req, out, handlers, imports);
        } catch (IOException e) {
            log.error("fork ['{}', '{}'] -> error creating a payload: {}", processKey, parentProcessKey, e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.startFork(payload));
    }

    /**
     * Waits for completion of a process.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/{id}/waitForCompletion")
    @Operation
    public ProcessEntry waitForCompletion(@PathParam("id") UUID instanceId,
                                          @QueryParam("timeout") @DefaultValue("-1") long timeout) {

        log.info("waitForCompletion ['{}', {}] -> waiting...", instanceId, timeout);

        long t1 = System.currentTimeMillis();

        ProcessEntry r;
        while (true) {
            r = get(instanceId);

            ProcessStatus s = r.status();
            if (s == ProcessStatus.FINISHED ||
                s == ProcessStatus.FAILED ||
                s == ProcessStatus.CANCELLED ||
                s == ProcessStatus.TIMED_OUT) {
                return r;
            }

            if (timeout > 0) {
                long t2 = System.currentTimeMillis();
                if (t2 - t1 >= timeout) {
                    log.warn("waitForCompletion ['{}', {}] -> timeout, last status: {}", instanceId, timeout, s);
                    throw new ConcordApplicationException(Response.status(Status.REQUEST_TIMEOUT).entity(r).build());
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) { // NOSONAR
                throw new ConcordApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("Request was interrputed")
                        .build());
            }
        }
    }

    /**
     * Disable a process.
     */
    @POST
    @javax.ws.rs.Path("/{id}/disable/{disabled}")
    @WithTimer
    @Operation
    public void disable(@PathParam("id") UUID instanceId,
                        @PathParam("disabled") boolean disabled) {
        ProcessKey processKey = assertProcessKey(instanceId);
        processManager.disable(processKey, disabled);
    }

    /**
     * Forcefully stops a process.
     */
    @DELETE
    @javax.ws.rs.Path("/{id}")
    @WithTimer
    @Operation
    public void kill(@PathParam("id") UUID instanceId) {
        ProcessKey processKey = assertProcessKey(instanceId);
        processManager.kill(processKey);
    }

    /**
     * Forcefully stops list of processes.
     */
    @DELETE
    @javax.ws.rs.Path("/bulk")
    @WithTimer
    @Operation(description = "Forcefully stop processes")
    public void batchKill(List<UUID> instanceIdList) {
        instanceIdList.forEach(this::kill);
    }

    /**
     * Forcefully stops a process and all its children.
     */
    @DELETE
    @javax.ws.rs.Path("/{id}/cascade")
    @WithTimer
    @Operation(description = "Forcefully stops a process and its all children")
    public void killCascade(@PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        processManager.killCascade(processKey);
    }

    /**
     * Returns a process instance details.
     *
     * @deprecated use {@link ProcessResourceV2#get(UUID, Set)}
     */
    @GET
    @javax.ws.rs.Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Deprecated
    public ProcessEntry get(@PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        ProcessEntry e = processQueueManager.get(processKey);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return e;
    }

    /**
     * Returns a process status history.
     */
    @GET
    @javax.ws.rs.Path("/{instanceId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get process status history")
    public List<ProcessStatusHistoryEntry> getStatusHistory(@PathParam("instanceId") UUID instanceId) throws IOException {
        ProcessKey pk = assertKey(instanceId);
        return queueDao.getStatusHistory(pk);
    }

    /**
     * Returns current process' wait conditions.
     */
    @GET
    @javax.ws.rs.Path("/{instanceId}/waits")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get process' wait conditions")
    public ProcessWaitEntry getWait(@PathParam("instanceId") UUID instanceId) {
        ProcessKey pk = assertKey(instanceId);
        return processWaitManager.getWait(pk);
    }

    /**
     * Returns a process' attachment file.
     */
    @GET
    @javax.ws.rs.Path("/{id}/attachment/{name:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(description = "Download a process' attachment")
    @ApiResponse(responseCode = "200", description = "File content",
            content = @Content(mediaType = "application/octet-stream",
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response downloadAttachment(@PathParam("id") UUID instanceId,
                                       @PathParam("name") @NotNull @Size(min = 1) String attachmentName) {

        ProcessEntry processEntry = processManager.assertProcess(instanceId);
        assertProcessAccess(processEntry, "attachment");
        PartialProcessKey processKey = new ProcessKey(processEntry.instanceId(), processEntry.createdAt());

        // TODO replace with javax.validation
        if (attachmentName.endsWith("/")) {
            throw new ConcordApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME, attachmentName);
        assertResourceAccess(processEntry, resource);

        Optional<Path> o = stateManager.get(processKey, resource, src -> {
            try {
                Path tmp = IOUtils.createTempFile("attachment", ".bin");
                try (OutputStream dst = Files.newOutputStream(tmp)) {
                    IOUtils.copy(src, dst);
                }
                return Optional.of(tmp);
            } catch (IOException e) {
                throw new ConcordApplicationException("Error while exporting an attachment: " + attachmentName, e);
            }
        });

        if (!o.isPresent()) {
            return Response.status(Status.NOT_FOUND).build();
        }

        Path tmp = o.get();

        return Response.ok((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(tmp)) {
                IOUtils.copy(in, out);
            } finally {
                Files.delete(tmp);
            }
        }).build();
    }

    /**
     * Lists process attachments.
     */
    @GET
    @javax.ws.rs.Path("/{id}/attachment")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List attachments")
    public List<String> listAttachments(@PathParam("id") UUID instanceId) {

        ProcessEntry processEntry = processManager.assertProcess(instanceId);
        assertProcessAccess(processEntry, "attachments");

        PartialProcessKey processKey = new ProcessKey(processEntry.instanceId(), processEntry.createdAt());

        String resource = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/";
        List<String> l = stateManager.list(processKey, resource);
        return l.stream()
                .map(s -> s.substring(resource.length()))
                .collect(Collectors.toList());
    }

    /**
     * List processes for all user's organizations
     *
     * @deprecated use {@link ProcessResourceV2#list(UUID, String, UUID, String, UUID, String, OffsetDateTimeParam, OffsetDateTimeParam, Set, ProcessStatus, String, UUID, Set, int, int, UriInfo)}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Deprecated
    public List<ProcessEntry> list(@QueryParam("org") String orgName,
                                   @QueryParam("project") String projectName,
                                   @QueryParam("projectId") UUID projectId,
                                   @QueryParam("afterCreatedAt") OffsetDateTimeParam afterCreatedAt,
                                   @QueryParam("beforeCreatedAt") OffsetDateTimeParam beforeCreatedAt,
                                   @QueryParam("tags") Set<String> tags,
                                   @QueryParam("status") ProcessStatus processStatus,
                                   @QueryParam("initiator") String initiator,
                                   @QueryParam("parentInstanceId") UUID parentId,
                                   @QueryParam("limit") @DefaultValue("30") int limit,
                                   @QueryParam("offset") @DefaultValue("0") int offset,
                                   @Context UriInfo uriInfo) {

        return v2.list(null, orgName, projectId, projectName, null, null, afterCreatedAt, beforeCreatedAt, tags,
                processStatus, initiator, parentId, Collections.singleton(ProcessDataInclude.CHILDREN_IDS),
                limit, offset, uriInfo);
    }

    /**
     * Returns a list of subprocesses for a given parent process.
     */
    @GET
    @javax.ws.rs.Path("/{id}/subprocess")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "List subprocesses of a parent process")
    public List<ProcessEntry> listSubprocesses(@PathParam("id") UUID parentInstanceId,
                                               @QueryParam("tags") Set<String> tags) {

        assertPartialKey(parentInstanceId);
        return queueDao.list(ProcessFilter.builder()
                .parentId(parentInstanceId)
                .tags(tags)
                .build());
    }

    @GET
    @javax.ws.rs.Path("/{id}/root")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Get super parent for a process")
    public ProcessEntry getRoot(@PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = assertPartialKey(instanceId);

        ProcessKey rootId = queueDao.getRootId(processKey);
        if (rootId == null) {
            return null;
        }

        return queueDao.get(rootId, Set.of());
    }

    /**
     * Updates a process' status
     */
    @POST
    @javax.ws.rs.Path("{id}/status")
    @Consumes(MediaType.TEXT_PLAIN)
    @WithTimer
    @Operation(description = "Update process status")
    public void updateStatus(@PathParam("id") UUID instanceId,
                             @Parameter(required = true) @QueryParam("agentId") String agentId,
                             @Parameter(required = true) ProcessStatus status) {

        ProcessKey processKey = assertProcessKey(instanceId);
        processManager.updateStatus(processKey, agentId, status);
    }

    /**
     * Retrieves a process' log.
     */
    @GET
    @javax.ws.rs.Path("/{id}/log")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Operation(description = "Retrieves a process' log", operationId = "getProcessLog")
    @ApiResponse(description = "Process log content",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response getLog(@PathParam("id") UUID instanceId,
                           @HeaderParam("range") String rangeHeader) {

        // check the permissions, logs can contain sensitive data
        ProcessKey processKey = logAccessManager.assertLogAccess(instanceId);

        HttpUtils.Range range = HttpUtils.parseRangeHeaderValue(rangeHeader);

        ProcessLog l = logManager.get(processKey, range.start(), range.end());
        return ProcessLogResourceV2.toResponse(instanceId, 0, l, range);
    }

    /**
     * Appends a process' log.
     *
     * @deprecated in favor of the /api/v2/process/{id}/log* endpoints
     */
    @POST
    @javax.ws.rs.Path("{id}/log")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Deprecated
    @Operation(description = "Appends a process' log", operationId = "appendProcessLog")
    @RequestBody(description = "Log content", required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    public void appendLog(@PathParam("id") UUID instanceId, InputStream data) {
        ProcessKey processKey = assertProcessKey(instanceId);

        try {
            byte[] ab = IOUtils.toByteArray(data);
            int upper = logManager.log(processKey, ab);

            // whenever we accept logs from an external source (e.g. from an Agent) we need to check
            // the log size limits
            int logSizeLimit = processCfg.getLogSizeLimit();
            if (upper >= logSizeLimit) {
                logManager.error(processKey, "Maximum log size reached: {}. Process cancelled.", logSizeLimit);
                processManager.kill(processKey);
            }
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while appending a log: " + e.getMessage());
        }
    }

    /**
     * Downloads the current state snapshot of a process.
     */
    @GET
    @javax.ws.rs.Path("/{id}/state/snapshot")
    @Produces("application/zip")
    @Operation(description = "Download a process state snapshot")
    @ApiResponse(responseCode = "200", description = "File content",
            content = @Content(mediaType = "application/zip",
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response downloadState(@PathParam("id") UUID instanceId) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        assertProcessAccess(entry, "state");

        StreamingOutput out = output -> {
            try (ZipArchiveOutputStream dst = new ZipArchiveOutputStream(output)) {
                stateManager.export(processKey, new ProcessStateManager.FilteringConsumer(zipTo(dst), s -> {
                    if (!isSessionResource(s)) {
                        return true;
                    }
                    return isSessionKeyAccess(instanceId);
                }));
            }
        };

        return Response.ok(out, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + instanceId + ".zip\"")
                .build();
    }

    /**
     * Downloads a single file from the current state snapshot of a process.
     */
    @GET
    @javax.ws.rs.Path("/{id}/state/snapshot/{name:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Validate
    @Operation(description = "Download a single file from a process state snapshot")
    @ApiResponse(responseCode = "200", description = "File content",
            content = @Content(mediaType = "application/octet-stream",
                    schema = @Schema(type = "string", format = "binary"))
    )
    public Response downloadStateFile(@PathParam("id") UUID instanceId,
                                      @PathParam("name") @NotNull @Size(min = 1) String fileName) {

        ProcessEntry p = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = new ProcessKey(p.instanceId(), p.createdAt());

        assertProcessAccess(p, "state");
        assertResourceAccess(p, fileName);

        StreamingOutput out = output -> {
            Path tmp = stateManager.get(processKey, fileName, ProcessResource::copyToTmp)
                    .orElseThrow(() -> new ConcordApplicationException("State file not found: " + fileName, Status.NOT_FOUND));

            try (InputStream in = Files.newInputStream(tmp)) {
                IOUtils.copy(in, output);
            } finally {
                Files.delete(tmp);
            }
        };

        return Response.ok(out)
                .build();
    }

    /**
     * Upload process attachments.
     */
    @POST
    @javax.ws.rs.Path("{id}/attachment")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(description = "Upload process attachments", operationId = "uploadProcessAttachments")
    @RequestBody(description = "Attachment content", required = true,
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    public void uploadAttachments(@PathParam("id") UUID instanceId, InputStream data) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        Path tmpIn = null;
        Path tmpDir = null;
        try {
            tmpIn = IOUtils.createTempFile("attachments", ".zip");
            Files.copy(data, tmpIn, StandardCopyOption.REPLACE_EXISTING);

            tmpDir = IOUtils.createTempDir("attachments");
            IOUtils.unzip(tmpIn, tmpDir);

            assertAttachmentsPolicy(tmpDir, entry);

            Path finalTmpDir = tmpDir;
            stateManager.tx(tx -> {
                stateManager.deleteDirectory(tx, processKey, path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME, Constants.Files.JOB_STATE_DIR_NAME));
                stateManager.importPath(tx, processKey, Constants.Files.JOB_ATTACHMENTS_DIR_NAME, finalTmpDir, (p, attrs) -> true);
            });

            Map<String, Object> out = OutVariablesUtils.read(tmpDir);
            if (out.isEmpty()) {
                queueDao.removeMeta(processKey, "out");
            } else {
                queueDao.updateMeta(processKey, Collections.singletonMap("out", out));
            }
        } catch (PolicyException e) {
            throw new ConcordApplicationException(e.getMessage(), Status.FORBIDDEN);
        } catch (IOException e) {
            log.error("uploadAttachments ['{}'] -> error", processKey, e);
            throw new ConcordApplicationException("upload error: " + e.getMessage());
        } finally {
            if (tmpDir != null) {
                try {
                    IOUtils.deleteRecursively(tmpDir);
                } catch (IOException e) {
                    log.warn("uploadAttachments -> cleanup error: {}", e.getMessage());
                }
            }
            if (tmpIn != null) {
                try {
                    Files.delete(tmpIn);
                } catch (IOException e) {
                    log.warn("uploadAttachments -> cleanup error: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Decrypt a base64 string previosly encrypted with the process' project key.
     */
    @POST
    @javax.ws.rs.Path("{id}/decrypt")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    @Operation(description = "Decrypt a base64 string previously encrypted with the process' project key", operationId = "decryptString")
    @ApiResponse(responseCode = "200", description = "Decrypted value",
            content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM,
                    schema = @Schema(type = "string", format = "byte")
            )
    )
    public Response decrypt(@PathParam("id") UUID instanceId,
                            @Parameter(schema = @Schema(type = "string", format = "byte"))
                            InputStream data) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        if (entry.projectId() == null) {
            throw new ConcordApplicationException("Project is required", Status.BAD_REQUEST);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        try {
            int read;
            byte[] buf = new byte[1024];
            while ((read = data.read(buf)) > 0) {
                baos.write(buf, 0, read);

                if (baos.size() > secretStoreCfg.getMaxEncryptedStringLength()) {
                    throw new ConcordApplicationException("Value too big, limit: " + secretStoreCfg.getMaxEncryptedStringLength(),
                            Status.BAD_REQUEST);
                }
            }
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while reading encrypted data: " + e.getMessage(), e);
        }

        try {
            UUID projectId = entry.projectId();
            byte[] result = encryptedValueManager.decrypt(projectId, baos.toByteArray());
            return Response.ok((StreamingOutput) output -> output.write(result))
                    .build();
        } catch (SecurityException e) {
            throw new ConcordApplicationException(e.getMessage(), Status.BAD_REQUEST);
        } catch (Exception e) {
            throw new ConcordApplicationException("Decrypt error: " + e.getMessage());
        }
    }

    /**
     * Update process metadata.
     */
    @POST
    @javax.ws.rs.Path("{id}/meta")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Update process metadata")
    public Response updateMetadata(@PathParam("id") UUID instanceId, Map<String, Object> meta) {
        ProcessKey processKey = assertProcessKey(instanceId);

        if (!queueDao.updateMeta(processKey, meta)) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        return Response.ok().build();
    }

    /**
     * Set the process' wait condition.
     */
    @POST
    @javax.ws.rs.Path("{id}/wait")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Operation(description = "Set the process' wait condition")
    public Response setWaitCondition(@PathParam("id") UUID instanceId, Map<String, Object> waitCondition) {
        ProcessKey processKey = assertProcessKey(instanceId);
        AbstractWaitCondition condition = objectMapper.convertValue(waitCondition, AbstractWaitCondition.class);
        processWaitManager.addWait(processKey, condition);
        return Response.ok().build();
    }

    private ProcessKey assertProcessKey(UUID instanceId) {
        ProcessKey processKey = processKeyCache.get(instanceId);
        if (processKey == null) {
            throw new ConcordApplicationException("Process instance not found: " + instanceId, Status.NOT_FOUND);
        }
        return processKey;
    }

    private void assertProcessAccess(ProcessEntry pe, String downloadEntity) {
        UserPrincipal principal = UserPrincipal.assertCurrent();

        UUID initiatorId = pe.initiatorId();
        if (principal.getId().equals(initiatorId)) {
            // process owners should be able to download the process' state
            return;
        }

        if (Roles.isAdmin() || Roles.isGlobalReader()) {
            return;
        }

        if (pe.projectId() != null) {
            projectAccessManager.assertAccess(pe.projectId(), ResourceAccessLevel.OWNER, true);
            return;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                                        "the necessary permissions to the download " + downloadEntity + " : " + pe.instanceId());
    }

    private void assertResourceAccess(ProcessEntry pe, String resource) {
        if (!isSessionResource(resource)) {
            return;
        }

        if (isSessionKeyAccess(pe.instanceId())) {
            return;
        }

        throw new UnauthorizedException("Resource accessible with session process key only");
    }

    private boolean isSessionResource(String resource) {
        return resource.startsWith(path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME, Constants.Files.JOB_SESSION_FILES_DIR_NAME));
    }

    private boolean isSessionKeyAccess(UUID instanceId) {
        SessionKeyPrincipal principal = SessionKeyPrincipal.getCurrent();
        return principal != null && principal.getProcessKey().getInstanceId().equals(instanceId);
    }

    private ProcessEntry assertProcess(PartialProcessKey processKey) {
        ProcessEntry p = processQueueManager.get(processKey);
        if (p == null) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return p;
    }

    private StartProcessResponse toResponse(ProcessResult r) {
        return new StartProcessResponse(r.getInstanceId());
    }

    private PartialProcessKey assertPartialKey(UUID id) {
        if (id == null) {
            return null;
        }

        PartialProcessKey k = PartialProcessKey.from(id);
        if (!queueDao.exists(k)) {
            throw new ValidationErrorsException("Unknown instance ID: " + id);
        }

        return k;
    }

    private ProcessKey assertKey(UUID id) {
        Optional<ProcessKey> key = processKeyCache.getUncached(id);
        return key.orElseThrow(() -> new ValidationErrorsException("Unknown instance ID: " + id));
    }

    private static boolean isEmpty(InputStream in) {
        try {
            return in.available() <= 0;
        } catch (IOException e) {
            throw new ConcordApplicationException("Internal error", e);
        }
    }

    private static Optional<Path> copyToTmp(InputStream in) {
        try {
            Path p = IOUtils.createTempFile("state", ".bin");
            try (OutputStream out = Files.newOutputStream(p)) {
                IOUtils.copy(in, out);
            }
            return Optional.of(p);
        } catch (IOException e) {
            throw new ConcordApplicationException("Error while copying a state file: " + e.getMessage(), e);
        }
    }

    private static RuntimeException syncIsForbidden() {
        return new ConcordApplicationException("The 'sync' mode is no longer available. " +
                                               "Please use sync=false and poll for the status updates.", Status.BAD_REQUEST);
    }

    private void assertAttachmentsPolicy(Path tmpDir, ProcessEntry entry) throws IOException {
        PolicyEngine policy = policyManager.get(entry.orgId(), entry.projectId(), UserPrincipal.assertCurrent().getUser().getId());
        if (policy == null) {
            return;
        }

        CheckResult<AttachmentsRule, Long> checkResult = policy.getAttachmentsPolicy().check(tmpDir);
        if (!checkResult.getDeny().isEmpty()) {
            String errorMessage = buildErrorMessage(checkResult.getDeny());
            processLogManager.error(new ProcessKey(entry.instanceId(), entry.createdAt()), errorMessage);
            throw new PolicyException("Found forbidden policy: " + errorMessage);
        }
    }

    private String buildErrorMessage(List<CheckResult.Item<AttachmentsRule, Long>> errors) {
        String defaultMessage = "Attachments too big: current {0} bytes, limit {1} bytes";

        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<AttachmentsRule, Long> e : errors) {
            AttachmentsRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : defaultMessage;
            long actualSize = e.getEntity();
            long limit = r.maxSizeInBytes();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actualSize, limit)).append(';');
        }
        return sb.toString();
    }
}
