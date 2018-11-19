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
import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.org.secret.SecretException;
import com.walmartlabs.concord.server.process.PayloadManager.EntryPoint;
import com.walmartlabs.concord.server.process.ProcessManager.ProcessResult;
import com.walmartlabs.concord.server.process.event.EventDao;
import com.walmartlabs.concord.server.process.event.ProcessEventEntry;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.state.archive.ProcessStateArchiver;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.authz.UnauthorizedException;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
@Singleton
@Api(value = "Process", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@javax.ws.rs.Path("/api/v1/process")
public class ProcessResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResource.class);

    private final ObjectMapper objectMapper;
    private final ProcessManager processManager;
    private final ProcessQueueDao queueDao;
    private final ProcessLogsDao logsDao;
    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final UserDao userDao;
    private final SecretStoreConfiguration secretStoreCfg;
    private final ProcessStateArchiver stateArchiver;
    private final EncryptedProjectValueManager encryptedValueManager;
    private final EventDao eventDao;
    private final ProcessKeyCache processKeyCache;

    @Inject
    public ProcessResource(ProcessManager processManager,
                           ProcessQueueDao queueDao,
                           ProcessLogsDao logsDao,
                           PayloadManager payloadManager,
                           ProcessStateManager stateManager,
                           UserDao userDao,
                           SecretStoreConfiguration secretStoreCfg,
                           ProcessStateArchiver stateArchiver,
                           EncryptedProjectValueManager encryptedValueManager,
                           EventDao eventDao,
                           ProcessKeyCache processKeyCache) {

        this.objectMapper = new ObjectMapper();
        this.processManager = processManager;
        this.queueDao = queueDao;
        this.logsDao = logsDao;
        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.userDao = userDao;
        this.secretStoreCfg = secretStoreCfg;
        this.stateArchiver = stateArchiver;
        this.encryptedValueManager = encryptedValueManager;
        this.eventDao = eventDao;
        this.processKeyCache = processKeyCache;
    }

    /**
     * Starts a new process instance.
     *
     * @param in
     * @param parentInstanceId
     * @param sync
     * @return
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @POST
    @ApiOperation("Start a new process instance using the supplied payload archive")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_octetstream")
    @Deprecated
    public StartProcessResponse start(@ApiParam InputStream in,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), in, out);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @param entryPoint
     * @param parentInstanceId
     * @param sync
     * @return
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point")
    @javax.ws.rs.Path("/{entryPoint}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_queryparams")
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        return start(entryPoint, Collections.emptyMap(), parentInstanceId, sync, out);
    }

    /**
     * Starts a new process instance using the specified entry point and provided configuration.
     *
     * @param entryPoint
     * @param req
     * @param parentInstanceId
     * @param sync
     * @return
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and provided configuration")
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_json")
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam Map<String, Object> req,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        assertPartialKey(parentInstanceId);

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(processKey, orgId, entryPoint);
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createPayload(processKey, parentInstanceId, userPrincipal.getId(), userPrincipal.getUsername(), ep, req, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    /**
     * Starts a new process instance.
     *
     * @param input
     * @param parentInstanceId
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Start a new process using multipart request data")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public StartProcessResponse start(@ApiParam MultipartInput input,
                                      @ApiParam @Deprecated @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @Deprecated @QueryParam("out") String[] out) {

        Payload payload;
        try {
            payload = payloadManager.createPayload(input);

            // TODO remove after deprecating the old endpoints
            payload = PayloadBuilder.basedOn(payload)
                    .parentInstanceId(parentInstanceId)
                    .mergeOutExpressions(out)
                    .build();
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        boolean sync2 = MultipartUtils.getBoolean(input, Constants.Multipart.SYNC, false);
        return toResponse(processManager.start(payload, sync || sync2));
    }

    /**
     * Starts a new process instance using the specified entry point and multipart request data.
     *
     * @param entryPoint
     * @param input
     * @param parentInstanceId
     * @param sync
     * @return
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and multipart request data")
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_with_entrypoint")
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam MultipartInput input,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

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

        return toResponse(processManager.start(payload, sync));
    }

    /**
     * Starts a new process instance using the specified entry point and payload archive.
     *
     * @param entryPoint
     * @param in
     * @param parentInstanceId
     * @param sync
     * @return
     * @deprecated use {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @POST
    @ApiOperation("Start a new process using the specified entry point and payload archive")
    @javax.ws.rs.Path("/{entryPoint}")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer(suffix = "_octetstream_and_entrypoint")
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam InputStream in,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

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
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    /**
     * Resumes an existing process.
     *
     * @param instanceId
     * @param eventName
     * @param req
     * @return
     */
    @POST
    @ApiOperation("Resume a process")
    @javax.ws.rs.Path("/{id}/resume/{eventName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public ResumeProcessResponse resume(@ApiParam @PathParam("id") UUID instanceId,
                                        @ApiParam @PathParam("eventName") @NotNull String eventName,
                                        @ApiParam Map<String, Object> req) {

        PartialProcessKey processKey = PartialProcessKey.from(instanceId);

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            log.error("resume ['{}', '{}'] -> error creating a payload: {}", instanceId, eventName, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        processManager.resume(payload);
        return new ResumeProcessResponse();
    }

    /**
     * Starts a new child process by forking the start of the specified parent process.
     *
     * @param parentInstanceId
     * @param req
     * @param sync
     * @return
     */
    @POST
    @ApiOperation("Fork a process")
    @javax.ws.rs.Path("/{id}/fork")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public StartProcessResponse fork(@ApiParam @PathParam("id") UUID parentInstanceId,
                                     @ApiParam Map<String, Object> req,
                                     @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                     @ApiParam @QueryParam("out") String[] out) {

        ProcessEntry parent = queueDao.get(PartialProcessKey.from(parentInstanceId));
        if (parent == null) {
            throw new ValidationErrorsException("Unknown parent instance ID: " + parentInstanceId);
        }

        PartialProcessKey processKey = PartialProcessKey.from(UUID.randomUUID());
        ProcessKey parentProcessKey = ProcessKey.from(parent);

        UUID projectId = parent.projectId();
        UserPrincipal userPrincipal = UserPrincipal.assertCurrent();

        Payload payload;
        try {
            payload = payloadManager.createFork(processKey, parentProcessKey, ProcessKind.DEFAULT,
                    userPrincipal.getId(), userPrincipal.getUsername(), projectId, req, out);
        } catch (IOException e) {
            log.error("fork ['{}', '{}'] -> error creating a payload: {}", processKey, parentProcessKey, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.startFork(payload, sync));
    }

    /**
     * Waits for completion of a process.
     *
     * @param instanceId
     * @param timeout
     * @return
     */
    @GET
    @ApiOperation("Wait for a process to finish")
    @Produces(MediaType.APPLICATION_JSON)
    @javax.ws.rs.Path("/{id}/waitForCompletion")
    public ProcessEntry waitForCompletion(@ApiParam @PathParam("id") UUID instanceId,
                                          @ApiParam @QueryParam("timeout") @DefaultValue("-1") long timeout) {

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
            } catch (InterruptedException e) {
                throw new ConcordApplicationException(Response.status(Status.INTERNAL_SERVER_ERROR)
                        .entity("Request was interrputed")
                        .build());
            }
        }
    }

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stops a process")
    @javax.ws.rs.Path("/{id}")
    @WithTimer
    public void kill(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessKey processKey = processKeyCache.get(instanceId);
        processManager.kill(processKey);
    }

    /**
     * Forcefully stops list of processes.
     *
     * @param instanceIdList
     */
    @DELETE
    @ApiOperation("Forcefully stop processes")
    @javax.ws.rs.Path("/bulk")
    @WithTimer
    public void batchKill(@ApiParam List<UUID> instanceIdList) {
        instanceIdList.forEach(this::kill);
    }

    /**
     * Forcefully stops a process and all its children.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stops a process and its all children")
    @javax.ws.rs.Path("/{id}/cascade")
    @WithTimer
    public void killCascade(@ApiParam @PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        processManager.killCascade(processKey);
    }

    /**
     * Returns a process instance details.
     *
     * @param instanceId
     * @return
     */
    @GET
    @ApiOperation("Get status of a process")
    @javax.ws.rs.Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @SuppressWarnings("unchecked")
    public ProcessEntry get(@ApiParam @PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        ProcessEntry e = queueDao.get(processKey);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return e;
    }


    /**
     * Returns a process status history.
     *
     * @param instanceId
     * @return
     */
    @GET
    @ApiOperation("Get process status history")
    @javax.ws.rs.Path("/{instanceId}/history")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @SuppressWarnings("unchecked")
    public List<ProcessStatusHistoryEntry> getStatusHistory(@ApiParam @PathParam("instanceId") UUID instanceId) throws IOException {
        ProcessKey pk = assertKey(instanceId);

        List<ProcessEventEntry> events = eventDao.list(pk, null, EventType.PROCESS_STATUS.name(), -1);
        List<ProcessStatusHistoryEntry> result = new ArrayList<>(events.size());
        for (ProcessEventEntry e : events) {
            Map<String, Object> payload = new HashMap<>(objectMapper.readValue((String) e.getData(), Map.class));
            ProcessStatus status = ProcessStatus.valueOf((String) payload.remove("status"));
            result.add(new ProcessStatusHistoryEntry(e.getId(), status, e.getEventDate(), payload));
        }

        return result;
    }

    /**
     * Returns a process' attachment file.
     *
     * @param instanceId
     * @param attachmentName
     * @return
     */
    @GET
    @ApiOperation(value = "Download a process' attachment", response = File.class)
    @javax.ws.rs.Path("/{id}/attachment/{name:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadAttachment(@ApiParam @PathParam("id") UUID instanceId,
                                       @PathParam("name") @NotNull @Size(min = 1) String attachmentName) {

        PartialProcessKey processKey = assertPartialKey(instanceId);

        // TODO replace with javax.validation
        if (attachmentName.endsWith("/")) {
            throw new ConcordApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, attachmentName);
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
     *
     * @param instanceId
     * @return
     */
    @GET
    @ApiOperation(value = "List attachments", responseContainer = "list", response = String.class)
    @javax.ws.rs.Path("/{id}/attachment")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<String> listAttachments(@ApiParam @PathParam("id") UUID instanceId) {
        PartialProcessKey processKey = assertPartialKey(instanceId);

        String resource = InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME + "/";
        List<String> l = stateManager.list(processKey, resource);
        return l.stream()
                .map(s -> s.substring(resource.length()))
                .collect(Collectors.toList());
    }

    /**
     * List processes for all user's organizations
     *
     * @param projectId
     * @param beforeCreatedAt
     * @param tags
     * @param processStatus
     * @param initiator
     * @param limit
     * @return
     */
    @GET
    @ApiOperation(value = "List processes for all user's organizations", responseContainer = "list", response = ProcessEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEntry> list(@ApiParam @QueryParam("projectId") UUID projectId,
                                   @ApiParam @QueryParam("afterCreatedAt") IsoDateParam afterCreatedAt,
                                   @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                                   @ApiParam @QueryParam("tags") Set<String> tags,
                                   @ApiParam @QueryParam("status") ProcessStatus processStatus,
                                   @ApiParam @QueryParam("initiator") String initiator,
                                   @ApiParam @QueryParam("parentInstanceId") UUID parentId,
                                   @ApiParam @QueryParam("limit") @DefaultValue("30") int limit,
                                   @ApiParam @QueryParam("offset") @DefaultValue("0") int offset) {

        if (limit <= 0) {
            throw new ConcordApplicationException("'limit' must be a positive number", Status.BAD_REQUEST);
        }

        Set<UUID> orgIds = null;
        if (!isAdmin()) {
            // non-admin users can see only their org's processes or processes w/o projects
            orgIds = getCurrentUserOrgIds();
        }

        ProcessFilter filter = ProcessFilter.builder()
                .parentId(parentId)
                .projectId(projectId)
                .includeWithoutProjects(true)
                .ordIds(orgIds)
                .afterCreatedAt(toTimestamp(afterCreatedAt))
                .beforeCreatedAt(toTimestamp(beforeCreatedAt))
                .tags(tags)
                .status(processStatus)
                .initiator(initiator)
                .build();

        return queueDao.list(filter, limit, offset);
    }

    /**
     * Returns a list of subprocesses for a given parent process.
     *
     * @param parentInstanceId
     * @param tags
     * @return
     */
    @GET
    @ApiOperation(value = "List subprocesses of a parent process", responseContainer = "list", response = ProcessEntry.class)
    @javax.ws.rs.Path("/{id}/subprocess")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEntry> listSubprocesses(@ApiParam @PathParam("id") UUID parentInstanceId,
                                               @ApiParam @QueryParam("tags") Set<String> tags) {

        assertPartialKey(parentInstanceId);
        return queueDao.list(parentInstanceId, tags);
    }

    /**
     * Updates a process' status
     *
     * @param instanceId
     * @param status
     */
    @POST
    @ApiOperation("Update process status")
    @javax.ws.rs.Path("{id}/status")
    @Consumes(MediaType.TEXT_PLAIN)
    @WithTimer
    public void updateStatus(@ApiParam @PathParam("id") UUID instanceId,
                             @ApiParam(required = true) @QueryParam("agentId") String agentId,
                             @ApiParam(required = true) ProcessStatus status) {

        ProcessKey processKey = processKeyCache.get(instanceId);
        processManager.updateStatus(processKey, agentId, status);
    }

    /**
     * Retrieves a process' log.
     *
     * @param instanceId
     * @param range
     * @return
     */
    @GET
    @ApiOperation(value = "Retrieve the log")
    @javax.ws.rs.Path("/{id}/log")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    public Response getLog(@ApiParam @PathParam("id") UUID instanceId,
                           @HeaderParam("range") String range) {

        ProcessKey processKey = assertKey(instanceId);

        Integer start = null;
        Integer end = null;

        if (range != null && !range.trim().isEmpty()) {
            if (!range.startsWith("bytes=")) {
                throw new ConcordApplicationException("Invalid range header: " + range, Status.BAD_REQUEST);
            }

            String[] as = range.substring("bytes=".length()).split("-");
            if (as.length > 0) {
                try {
                    start = Integer.parseInt(as[0]);
                } catch (NumberFormatException ignored) {
                }
            }

            if (as.length > 1) {
                try {
                    end = Integer.parseInt(as[1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        ProcessLog l = logsDao.get(processKey, start, end);
        List<ProcessLogChunk> data = l.getChunks();
        // TODO check if the instance actually exists

        if (data.isEmpty()) {
            int actualStart = start != null ? start : 0;
            int actualEnd = end != null ? end : start;
            return Response.ok()
                    .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/" + l.getSize())
                    .build();
        }

        ProcessLogChunk first = data.get(0);
        int actualStart = first.getStart();

        ProcessLogChunk last = data.get(data.size() - 1);
        int actualEnd = last.getStart() + last.getData().length;

        StreamingOutput out = output -> {
            for (ProcessLogChunk e : data) {
                output.write(e.getData());
            }
        };

        return Response.ok(out)
                .header("Content-Range", "bytes " + actualStart + "-" + actualEnd + "/" + l.getSize())
                .build();
    }

    /**
     * Appends a process' log.
     *
     * @param instanceId
     * @param data
     */
    @POST
    @javax.ws.rs.Path("{id}/log")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    public void appendLog(@PathParam("id") UUID instanceId, InputStream data) {
        ProcessKey processKey = processKeyCache.get(instanceId);

        try {
            logsDao.append(processKey, ByteStreams.toByteArray(data));
        } catch (IOException e) {
            log.error("appendLog ['{}'] -> error", instanceId, e);
            throw new ConcordApplicationException("append log error: " + e.getMessage());
        }
    }

    /**
     * Check process state archive status
     */
    @GET
    @ApiOperation(value = "Check process state archive status", response = Boolean.class)
    @javax.ws.rs.Path("/{id}/state/archived/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Boolean isStateArchived(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));

        assertProcessStateAccess(entry);
        ProcessKey processKey = ProcessKey.from(entry);

        return stateArchiver.isArchived(processKey);
    }

    /**
     * Downloads the current state snapshot of a process.
     */
    @GET
    @ApiOperation(value = "Download a process state snapshot", response = File.class)
    @javax.ws.rs.Path("/{id}/state/snapshot")
    @Produces("application/zip")
    public Response downloadState(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = ProcessKey.from(entry);

        assertProcessStateAccess(entry);

        StreamingOutput out = output -> stateArchiver.export(processKey, output);
        return Response.ok(out, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + instanceId + ".zip\"")
                .build();
    }

    /**
     * Downloads a single file from the current state snapshot of a process.
     */
    @GET
    @ApiOperation(value = "Download a single file from a process state snapshot", response = File.class)
    @javax.ws.rs.Path("/{id}/state/snapshot/{name:.*}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Validate
    public Response downloadStateFile(@ApiParam @PathParam("id") UUID instanceId,
                                      @ApiParam @PathParam("name") @NotNull @Size(min = 1) String fileName) {

        ProcessEntry p = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = ProcessKey.from(p);

        assertProcessStateAccess(p);

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
     *
     * @param instanceId
     * @param data
     */
    @POST
    @javax.ws.rs.Path("{id}/attachment")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public void uploadAttachments(@PathParam("id") UUID instanceId, InputStream data) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = ProcessKey.from(entry);

        Path tmpIn = null;
        Path tmpDir = null;
        try {
            tmpIn = IOUtils.createTempFile("attachments", ".zip");
            Files.copy(data, tmpIn, StandardCopyOption.REPLACE_EXISTING);

            tmpDir = IOUtils.createTempDir("attachments");
            IOUtils.unzip(tmpIn, tmpDir);

            stateManager.deleteDirectory(processKey, path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, InternalConstants.Files.JOB_STATE_DIR_NAME));
            stateManager.importPath(processKey, InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, tmpDir);

            Map<String, Object> out = OutVariablesUtils.read(tmpDir);
            if (out.isEmpty()) {
                queueDao.removeMeta(processKey, "out");
            } else {
                queueDao.updateMeta(processKey, Collections.singletonMap("out", out));
            }
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

        log.info("uploadAttachments ['{}'] -> done", processKey);
    }

    /**
     * Decrypt string.
     *
     * @param instanceId
     * @param data
     * @return
     */
    @POST
    @javax.ws.rs.Path("{id}/decrypt")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @WithTimer
    public Response decrypt(@PathParam("id") UUID instanceId, InputStream data) {
        ProcessEntry entry = assertProcess(PartialProcessKey.from(instanceId));
        ProcessKey processKey = ProcessKey.from(entry);

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

        byte[] result;
        try {
            UUID projectId = entry.projectId();
            result = encryptedValueManager.decrypt(projectId, baos.toByteArray());
        } catch (SecurityException e) {
            log.error("decrypt ['{}'] -> error", processKey, e);
            throw new SecretException("Decrypt error: " + e.getMessage());
        } catch (Exception e) {
            log.error("decrypt ['{}'] -> error", processKey, e);
            throw new ConcordApplicationException("Decrypt error: " + e.getMessage());
        }

        return Response.ok((StreamingOutput) output -> output.write(result))
                .build();
    }

    /**
     * Update process metadata.
     *
     * @param instanceId
     * @param meta
     * @return
     */
    @POST
    @ApiOperation(value = "Update process metadata")
    @javax.ws.rs.Path("{id}/meta")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public Response updateMetadata(@ApiParam @PathParam("id") UUID instanceId, @ApiParam Map<String, Object> meta) {
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);

        if (!queueDao.updateMeta(processKey, meta)) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        return Response.ok().build();
    }

    private void assertProcessStateAccess(ProcessEntry p) {
        UserPrincipal principal = UserPrincipal.assertCurrent();

        UUID initiatorId = p.initiatorId();
        if (principal.getId().equals(initiatorId)) {
            // process owners should be able to download the process' state
            return;
        }

        if (principal.isAdmin() || principal.isGlobalReader()) {
            return;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                "the necessary permissions to the download the process state: " + p.instanceId());
    }

    private ProcessEntry assertProcess(PartialProcessKey processKey) {
        ProcessEntry p = queueDao.get(processKey);
        if (p == null) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return p;
    }

    private StartProcessResponse toResponse(ProcessResult r) {
        return new StartProcessResponse(r.getInstanceId(), r.getOut());
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

    private Set<UUID> getCurrentUserOrgIds() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        return userDao.getOrgIds(p.getId());
    }

    private static boolean isAdmin() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        return p.isAdmin();
    }

    private static Timestamp toTimestamp(IsoDateParam p) {
        if (p == null) {
            return null;
        }

        Calendar c = p.getValue();
        return new Timestamp(c.getTimeInMillis());
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
}
