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

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.IsoDateParam;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretException;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.process.PayloadManager.EntryPoint;
import com.walmartlabs.concord.server.process.ProcessManager.ProcessResult;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.state.archive.ProcessStateArchiver;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.subject.Subject;
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

    private final ProcessManager processManager;
    private final ProcessQueueDao queueDao;
    private final ProcessLogsDao logsDao;
    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final UserDao userDao;
    private final SecretManager secretManager;
    private final SecretStoreConfiguration secretStoreCfg;
    private final ProcessStateArchiver stateArchiver;

    @Inject
    public ProcessResource(ProcessManager processManager,
                           ProcessQueueDao queueDao,
                           ProcessLogsDao logsDao,
                           PayloadManager payloadManager,
                           ProcessStateManager stateManager,
                           UserDao userDao,
                           SecretManager secretManager,
                           SecretStoreConfiguration secretStoreCfg,
                           ProcessStateArchiver stateArchiver) {

        this.processManager = processManager;
        this.queueDao = queueDao;
        this.logsDao = logsDao;
        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.userDao = userDao;
        this.secretManager = secretManager;
        this.secretStoreCfg = secretStoreCfg;
        this.stateArchiver = stateArchiver;
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
    @Deprecated
    public StartProcessResponse start(@ApiParam InputStream in,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), in, out);
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
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam Map<String, Object> req,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(instanceId, orgId, entryPoint);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), ep, req, out);
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
    public StartProcessResponse start(@ApiParam MultipartInput input,
                                      @ApiParam @Deprecated @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @Deprecated @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @Deprecated @QueryParam("out") String[] out) {

        Payload payload;
        try {
            payload = payloadManager.createPayload(input);

            // TODO remove after deprecating the old endpoints
            payload = new PayloadBuilder(payload)
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
    @Deprecated
    public StartProcessResponse start(@ApiParam @PathParam("entryPoint") String entryPoint,
                                      @ApiParam MultipartInput input,
                                      @ApiParam @QueryParam("parentId") UUID parentInstanceId,
                                      @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                      @ApiParam @QueryParam("out") String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(instanceId, orgId, entryPoint);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), ep, input, out);
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

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(instanceId, orgId, entryPoint);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), ep, in, out);
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
    public ResumeProcessResponse resume(@ApiParam @PathParam("id") UUID instanceId,
                                        @ApiParam @PathParam("eventName") @NotNull String eventName,
                                        @ApiParam Map<String, Object> req) {

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(instanceId, eventName, req);
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
    public StartProcessResponse fork(@ApiParam @PathParam("id") UUID parentInstanceId,
                                     @ApiParam Map<String, Object> req,
                                     @ApiParam @DefaultValue("false") @QueryParam("sync") boolean sync,
                                     @ApiParam @QueryParam("out") String[] out) {

        ProcessEntry parent = queueDao.get(parentInstanceId);
        if (parent == null) {
            throw new ValidationErrorsException("Unknown parent instance ID: " + parentInstanceId);
        }

        UUID instanceId = UUID.randomUUID();
        UUID projectId = parent.getProjectId();

        Payload payload;
        try {
            payload = payloadManager.createFork(instanceId, parentInstanceId, ProcessKind.DEFAULT,
                    getInitiator(), projectId, req, out);
        } catch (IOException e) {
            log.error("fork ['{}', '{}'] -> error creating a payload: {}", instanceId, parentInstanceId, e);
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
            if (r.getStatus() == ProcessStatus.FINISHED || r.getStatus() == ProcessStatus.FAILED || r.getStatus() == ProcessStatus.CANCELLED) {
                break;
            }

            if (timeout > 0) {
                long t2 = System.currentTimeMillis();
                if (t2 - t1 >= timeout) {
                    log.warn("waitForCompletion ['{}', {}] -> timeout, last status: {}", instanceId, timeout, r.getStatus());
                    throw new ConcordApplicationException(Response.status(Status.REQUEST_TIMEOUT).entity(r).build());
                }
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        return r;
    }

    /**
     * Forcefully stops a process.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stops a process")
    @javax.ws.rs.Path("/{id}")
    public void kill(@ApiParam @PathParam("id") UUID instanceId) {
        processManager.kill(instanceId);
    }

    /**
     * Forcefully stops a process and all its children.
     *
     * @param instanceId
     */
    @DELETE
    @ApiOperation("Forcefully stops a process and its all children")
    @javax.ws.rs.Path("/{id}/cascade")
    public void killCascade(@ApiParam @PathParam("id") UUID instanceId) {
        processManager.killCascade(instanceId);
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
    public ProcessEntry get(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return e;
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

        assertInstanceId(instanceId);

        // TODO replace with javax.validation
        if (attachmentName.endsWith("/")) {
            throw new ConcordApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
        }

        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, attachmentName);
        Optional<Path> o = stateManager.get(instanceId, resource, src -> {
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
    public List<String> listAttachments(@ApiParam @PathParam("id") UUID instanceId) {
        assertInstanceId(instanceId);

        String resource = InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME + "/";
        List<String> l = stateManager.list(instanceId, resource);
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
     * @param limit
     * @return
     */
    @GET
    @ApiOperation(value = "List processes for all user's organizations", responseContainer = "list", response = ProcessEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProcessEntry> list(@ApiParam @QueryParam("projectId") UUID projectId,
                                   @ApiParam @QueryParam("beforeCreatedAt") IsoDateParam beforeCreatedAt,
                                   @ApiParam @QueryParam("tags") Set<String> tags,
                                   @ApiParam @QueryParam("limit") @DefaultValue("30") int limit) {

        if (limit <= 0) {
            throw new ConcordApplicationException("'limit' must be a positive number", Status.BAD_REQUEST);
        }

        Set<UUID> orgIds = null;
        if (!isAdmin()) {
            // non-admin users can see only their org's processes or processes w/o projects
            orgIds = getCurrentUserOrgIds();
        }
        return queueDao.list(orgIds, true, projectId, toTimestamp(beforeCreatedAt), tags, limit);
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
    public List<ProcessEntry> listSubprocesses(@ApiParam @PathParam("id") UUID parentInstanceId,
                                               @ApiParam @QueryParam("tags") Set<String> tags) {

        assertInstanceId(parentInstanceId);
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
    public void updateStatus(@ApiParam @PathParam("id") UUID instanceId,
                             @ApiParam(required = true) @QueryParam("agentId") String agentId,
                             @ApiParam(required = true) ProcessStatus status) {

        processManager.updateStatus(instanceId, agentId, status);
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
    public Response getLog(@ApiParam @PathParam("id") UUID instanceId,
                           @HeaderParam("range") String range) {

        assertInstanceId(instanceId);

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

        ProcessLog l = logsDao.get(instanceId, start, end);
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
        assertProcess(instanceId);

        try {
            logsDao.append(instanceId, ByteStreams.toByteArray(data));
        } catch (IOException e) {
            log.error("appendLog ['{}'] -> error", instanceId, e);
            throw new ConcordApplicationException("append log error: " + e.getMessage());
        }
    }

    /**
     * Downloads the current state snapshot of a process.
     */
    @GET
    @ApiOperation(value = "Download a process state snapshot", response = File.class)
    @javax.ws.rs.Path("/{id}/state/snapshot")
    @Produces("application/zip")
    public Response downloadState(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry p = assertProcess(instanceId);

        assertProcessStateAccess(p);

        StreamingOutput out = output -> stateArchiver.export(instanceId, output);
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

        ProcessEntry p = assertProcess(instanceId);

        assertProcessStateAccess(p);

        StreamingOutput out = output -> {
            Path tmp = stateManager.get(instanceId, fileName, ProcessResource::copyToTmp)
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
        assertProcess(instanceId);

        Path tmpIn = null;
        Path tmpDir = null;
        try {
            tmpIn = IOUtils.createTempFile("attachments", ".zip");
            Files.copy(data, tmpIn, StandardCopyOption.REPLACE_EXISTING);

            tmpDir = IOUtils.createTempDir("attachments");
            IOUtils.unzip(tmpIn, tmpDir);

            Path finalTmpDir = tmpDir;
            stateManager.delete(instanceId, path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, InternalConstants.Files.JOB_STATE_DIR_NAME));
            stateManager.importPath(instanceId, InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, finalTmpDir);
        } catch (IOException e) {
            log.error("uploadAttachments ['{}'] -> error", instanceId, e);
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

        log.info("uploadAttachments ['{}'] -> done", instanceId);
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
        ProcessEntry p = assertProcess(instanceId);

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
            result = secretManager.decryptData(p.getProjectName(), baos.toByteArray());
        } catch (SecurityException e) {
            log.error("decrypt ['{}'] -> error", instanceId, e);
            throw new SecretException("Decrypt error: " + e.getMessage());
        } catch (Exception e) {
            log.error("decrypt ['{}'] -> error", instanceId, e);
            throw new ConcordApplicationException("Decrypt error: " + e.getMessage());
        }

        return Response.ok((StreamingOutput) output -> output.write(result))
                .build();
    }

    private void assertProcessStateAccess(ProcessEntry p) {
        UserPrincipal principal = UserPrincipal.assertCurrent();

        String initiator = p.getInitiator();
        if (principal.getUsername().equals(initiator)) {
            // process owners should be able to download the process' state
            return;
        }

        if (principal.isAdmin() || principal.isGlobalReader()) {
            return;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                "the necessary permissions to the download the process state: " + p.getInstanceId());
    }

    private ProcessEntry assertProcess(UUID instanceId) {
        ProcessEntry p = queueDao.get(instanceId);
        if (p == null) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return p;
    }

    private StartProcessResponse toResponse(ProcessResult r) {
        return new StartProcessResponse(r.getInstanceId(), r.getOut());
    }

    private void assertInstanceId(UUID id) {
        if (id == null) {
            return;
        }

        if (!queueDao.exists(id)) {
            throw new ValidationErrorsException("Unknown parent instance ID: " + id);
        }
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

    private static String getInitiator() {
        Subject subject = SecurityUtils.getSubject();
        if (subject == null || !subject.isAuthenticated()) {
            return null;
        }

        UserPrincipal p = (UserPrincipal) subject.getPrincipal();
        return p.getUsername();
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
