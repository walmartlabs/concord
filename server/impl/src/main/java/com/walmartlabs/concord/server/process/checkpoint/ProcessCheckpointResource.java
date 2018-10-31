package com.walmartlabs.concord.server.process.checkpoint;

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
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;


@Named
@Singleton
@Api(value = "Checkpoint", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@javax.ws.rs.Path("/api/v1/process")
public class ProcessCheckpointResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessCheckpointResource.class);

    private static final Set<ProcessStatus> RESTORE_ALLOWED_STATUSES = new HashSet<>(Arrays.asList(
            ProcessStatus.FAILED, ProcessStatus.FINISHED, ProcessStatus.SUSPENDED, ProcessStatus.TIMED_OUT));

    private final ProcessManager processManager;
    private final PayloadManager payloadManager;
    private final ProcessCheckpointManager checkpointManager;
    private final ProcessQueueDao processQueueDao;
    private final ProjectAccessManager projectAccessManager;

    @Inject
    public ProcessCheckpointResource(ProcessManager processManager,
                                     PayloadManager payloadManager,
                                     ProcessCheckpointManager checkpointManager,
                                     ProcessQueueDao processQueueDao,
                                     ProjectAccessManager projectAccessManager) {
        this.processManager = processManager;
        this.payloadManager = payloadManager;
        this.checkpointManager = checkpointManager;
        this.processQueueDao = processQueueDao;
        this.projectAccessManager = projectAccessManager;
    }

    @GET
    @ApiOperation(value = "List the process checkpoints")
    @javax.ws.rs.Path("{id}/checkpoint")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessCheckpointEntry> list(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry entry = assertProcess(instanceId);
        ProcessKey processKey = ProcessKey.from(entry);

        assertProcessCheckpointAccess(entry);

        return checkpointManager.list(processKey);
    }

    @POST
    @ApiOperation(value = "Restore process from checkpoint")
    @javax.ws.rs.Path("{id}/checkpoint/restore")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    @Validate
    public ResumeProcessResponse restore(@ApiParam @PathParam("id") UUID instanceId,
                                         @ApiParam @Valid RestoreCheckpointRequest request) {

        UUID checkpointId = request.getId();

        ProcessEntry entry = assertProcess(instanceId);
        ProcessKey processKey = ProcessKey.from(entry);

        assertProcessCheckpointAccess(entry);

        ProcessStatus status = entry.getStatus();
        if (!RESTORE_ALLOWED_STATUSES.contains(status)) {
            throw new ConcordApplicationException("Unable to restore a checkpoint, the process is " + status);
        }

        String eventName = checkpointManager.restoreCheckpoint(processKey, checkpointId);
        if (eventName == null) {
            throw new ConcordApplicationException("Checkpoint " + checkpointId + " not found");
        }

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, null);
        } catch (IOException e) {
            log.error("restore ['{}', '{}'] -> error creating a payload: {}", processKey, eventName, e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        processQueueDao.updateStatus(processKey, ProcessStatus.SUSPENDED);

        processManager.resume(payload);
        return new ResumeProcessResponse();
    }

    @POST
    @javax.ws.rs.Path("{id}/checkpoint")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void uploadCheckpoint(@PathParam("id") UUID instanceId,
                                 @ApiParam MultipartInput input) {

        ProcessEntry entry = assertProcess(instanceId);
        ProcessKey processKey = ProcessKey.from(entry);

        UUID checkpointId = MultipartUtils.getUuid(input, "id");
        String checkpointName = MultipartUtils.getString(input, "name");
        try (InputStream data = MultipartUtils.getStream(input, "data");
             TemporaryPath tmpIn = IOUtils.tempFile("checkpoint", ".zip")) {

            Files.copy(data, tmpIn.path(), StandardCopyOption.REPLACE_EXISTING);
            checkpointManager.importCheckpoint(processKey, checkpointId, checkpointName, tmpIn.path());
        } catch (IOException e) {
            log.error("uploadCheckpoint ['{}'] -> error", processKey, e);
            throw new ConcordApplicationException("upload error: " + e.getMessage());
        }

        log.info("uploadCheckpoint ['{}'] -> done", processKey);
    }

    private ProcessEntry assertProcess(UUID instanceId) {
        ProcessEntry p = processQueueDao.get(PartialProcessKey.from(instanceId));
        if (p == null) {
            throw new ConcordApplicationException("Process instance not found", Response.Status.NOT_FOUND);
        }
        return p;
    }

    private void assertProcessCheckpointAccess(ProcessEntry p) {
        UserPrincipal principal = UserPrincipal.assertCurrent();

        UUID initiatorId = p.getInitiatorId();
        if (principal.getId().equals(initiatorId)) {
            // process owners should be able to restore the process from a checkpoint
            return;
        }

        if (principal.isAdmin()) {
            return;
        }

        if (p.getProjectId() != null) {
            projectAccessManager.assertProjectAccess(p.getProjectId(), ResourceAccessLevel.WRITER, true);
            return;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                "the necessary permissions to restore the process using a checkpoint: " + p.getInstanceId());
    }
}
