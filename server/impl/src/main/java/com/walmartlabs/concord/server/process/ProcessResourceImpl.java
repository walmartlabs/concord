package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.IsoDateParam;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.process.*;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.PayloadManager.EntryPoint;
import com.walmartlabs.concord.server.process.ProcessManager.ProcessResult;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLog;
import com.walmartlabs.concord.server.process.logs.ProcessLogsDao.ProcessLogChunk;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@Named
public class ProcessResourceImpl implements ProcessResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessResourceImpl.class);

    private final ProcessManager processManager;
    private final ProcessQueueDao queueDao;
    private final ProcessLogsDao logsDao;
    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final UserDao userDao;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public ProcessResourceImpl(ProcessManager processManager,
                               ProcessQueueDao queueDao,
                               ProcessLogsDao logsDao,
                               PayloadManager payloadManager,
                               ProcessStateManager stateManager,
                               UserDao userDao,
                               ProjectAccessManager projectAccessManager) {

        this.processManager = processManager;
        this.queueDao = queueDao;
        this.logsDao = logsDao;
        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.userDao = userDao;
        this.projectAccessManager = projectAccessManager;
    }

    @Override
    public StartProcessResponse start(InputStream in, UUID parentInstanceId,
                                      boolean sync, String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), in, out);
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    @Override
    public StartProcessResponse start(String entryPoint, UUID parentInstanceId,
                                      boolean sync, String[] out) {

        return start(entryPoint, Collections.emptyMap(), parentInstanceId, sync, out);
    }

    /**
     * @deprecated prefer {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @Override
    @Deprecated
    public StartProcessResponse start(String entryPoint, Map<String, Object> req, UUID parentInstanceId,
                                      boolean sync, String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(instanceId, orgId, entryPoint);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), ep, req, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    @Override
    public StartProcessResponse start(MultipartInput input, UUID parentInstanceId,
                                      boolean sync, String[] out) {

        Payload payload;
        try {
            payload = payloadManager.createPayload(input);

            // TODO remove after deprecating the old endpoints
            payload = new PayloadBuilder(payload)
                    .parentInstanceId(parentInstanceId)
                    .outExpressions(out)
                    .build();
        } catch (IOException e) {
            log.error("start -> error creating a payload: {}", e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        boolean sync2 = MultipartUtils.getBoolean(input, "sync", false);
        return toResponse(processManager.start(payload, sync || sync2));
    }

    /**
     * @deprecated prefer {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @Override
    @Deprecated
    public StartProcessResponse start(String entryPoint, MultipartInput input, UUID parentInstanceId,
                                      boolean sync, String[] out) {

        assertInstanceId(parentInstanceId);

        UUID instanceId = UUID.randomUUID();

        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;
        EntryPoint ep = payloadManager.parseEntryPoint(instanceId, orgId, entryPoint);

        Payload payload;
        try {
            payload = payloadManager.createPayload(instanceId, parentInstanceId, getInitiator(), ep, input, out);
        } catch (IOException e) {
            log.error("start ['{}'] -> error creating a payload: {}", entryPoint, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    /**
     * @deprecated prefer {@link #start(MultipartInput, UUID, boolean, String[])}
     */
    @Override
    @Deprecated
    public StartProcessResponse start(String entryPoint, InputStream in, UUID parentInstanceId,
                                      boolean sync, String[] out) {

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
            throw new WebApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.start(payload, sync));
    }

    @Override
    public ResumeProcessResponse resume(UUID instanceId, String eventName, Map<String, Object> req) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(instanceId, eventName, req);
        } catch (IOException e) {
            log.error("resume ['{}', '{}'] -> error creating a payload: {}", instanceId, eventName, e);
            throw new WebApplicationException("Error creating a payload", e);
        }

        processManager.resume(payload);
        return new ResumeProcessResponse();
    }

    @Override
    public StartProcessResponse fork(UUID parentInstanceId, Map<String, Object> req, boolean sync, String[] out) {
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
            throw new WebApplicationException("Error creating a payload", e);
        }

        return toResponse(processManager.startFork(payload, sync));
    }

    @Override
    public ProcessEntry waitForCompletion(UUID instanceId, long timeout) {
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
                    throw new WebApplicationException(Response.status(Status.REQUEST_TIMEOUT).entity(r).build());
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

    @Override
    @Validate
    public void kill(UUID instanceId) {
        processManager.kill(instanceId);
    }

    @Override
    @Validate
    public void killCascade(UUID parentInstanceId) {
        processManager.killCascade(parentInstanceId);
    }

    @Override
    public ProcessEntry get(UUID instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            log.warn("get ['{}'] -> not found", instanceId);
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return e;
    }

    @Override
    @Validate
    public Response downloadAttachment(UUID instanceId, String attachmentName) {
        assertInstanceId(instanceId);

        // TODO replace with javax.validation
        if (attachmentName.endsWith("/")) {
            throw new WebApplicationException("Invalid attachment name: " + attachmentName, Status.BAD_REQUEST);
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
                throw new WebApplicationException("Error while exporting an attachment: " + attachmentName, e);
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

    @Override
    public List<String> listAttachments(UUID instanceId) {
        assertInstanceId(instanceId);

        String resource = InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME + "/";
        List<String> l = stateManager.list(instanceId, resource);
        return l.stream()
                .map(s -> s.substring(resource.length()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProcessEntry> list(UUID projectId, IsoDateParam beforeCreatedAt, Set<String> tags, int limit) {
        if (limit <= 0) {
            throw new WebApplicationException("'limit' must be a positive number", Status.BAD_REQUEST);
        }

        Set<UUID> orgIds = null;
        if (!isAdmin()) {
            // non-admin users can see only their org's processes or processes w/o projects
            orgIds = getCurrentUserOrgIds();
        }
        return queueDao.list(orgIds, true, projectId, toTimestamp(beforeCreatedAt), tags, limit);
    }

    @Override
    public List<ProcessEntry> list(UUID parentInstanceId, Set<String> tags) {
        assertInstanceId(parentInstanceId);
        return queueDao.list(parentInstanceId, tags);
    }

    @Override
    public void updateStatus(UUID instanceId, String agentId, ProcessStatus status) {
        processManager.updateStatus(instanceId, agentId, status);
    }

    @Override
    public Response getLog(UUID instanceId, String range) {
        assertInstanceId(instanceId);

        Integer start = null;
        Integer end = null;

        if (range != null && !range.trim().isEmpty()) {
            if (!range.startsWith("bytes=")) {
                throw new WebApplicationException("Invalid range header: " + range, Status.BAD_REQUEST);
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

    @Override
    public void appendLog(UUID instanceId, InputStream data) {
        ProcessEntry p = queueDao.get(instanceId);
        if (p == null) {
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        try {
            logsDao.append(instanceId, ByteStreams.toByteArray(data));
        } catch (IOException e) {
            log.error("appendLog ['{}'] -> error", instanceId, e);
            throw new WebApplicationException("append log error: " + e.getMessage());
        }
    }

    @Override
    public Response downloadState(UUID instanceId) {
        ProcessEntry p = queueDao.get(instanceId);
        if (p == null) {
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        if (p.getProjectId() != null) {
            projectAccessManager.assertProjectAccess(p.getProjectId(), ResourceAccessLevel.READER, false);
        }

        StreamingOutput out = output -> {
            try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output)) {
                stateManager.export(instanceId, zipTo(zip));
            }
        };

        return Response.ok(out)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + instanceId + ".zip\"")
                .build();
    }

    @Override
    @Validate
    public Response downloadState(UUID instanceId, String fileName) {
        ProcessEntry p = queueDao.get(instanceId);
        if (p == null) {
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }

        if (p.getProjectId() != null) {
            projectAccessManager.assertProjectAccess(p.getProjectId(), ResourceAccessLevel.READER, false);
        }

        StreamingOutput out = output -> {
            Path tmp = stateManager.get(instanceId, fileName, ProcessResourceImpl::copyToTmp)
                    .orElseThrow(() -> new WebApplicationException("State file not found: " + fileName, Status.NOT_FOUND));

            try (InputStream in = Files.newInputStream(tmp)) {
                IOUtils.copy(in, output);
            } finally {
                Files.delete(tmp);
            }
        };

        return Response.ok(out)
                .build();
    }

    @Override
    public void uploadAttachments(UUID instanceId, InputStream data) {
        ProcessEntry p = queueDao.get(instanceId);
        if (p == null) {
            throw new WebApplicationException("Process instance not found", Status.NOT_FOUND);
        }

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
            throw new WebApplicationException("upload error: " + e.getMessage());
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
            throw new WebApplicationException("Internal error", e);
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
            throw new WebApplicationException("Error while copying a state file: " + e.getMessage(), e);
        }
    }
}
