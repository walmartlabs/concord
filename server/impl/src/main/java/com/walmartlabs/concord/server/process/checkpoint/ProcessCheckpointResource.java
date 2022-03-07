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
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessEntry.ProcessCheckpointEntry;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.ResumeProcessResponse;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

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
import java.util.List;
import java.util.UUID;


@Named
@Singleton
@Api(value = "Checkpoint", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@javax.ws.rs.Path("/api/v1/process")
public class ProcessCheckpointResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(ProcessCheckpointResource.class);

    private final ProcessManager processManager;
    private final ProcessCheckpointManager checkpointManager;

    @Inject
    public ProcessCheckpointResource(ProcessManager processManager,
                                     ProcessCheckpointManager checkpointManager) {
        this.processManager = processManager;
        this.checkpointManager = checkpointManager;
    }

    @GET
    @ApiOperation(value = "List the process checkpoints", responseContainer = "list", response = ProcessCheckpointEntry.class)
    @javax.ws.rs.Path("{id}/checkpoint")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessCheckpointEntry> list(@ApiParam @PathParam("id") UUID instanceId) {
        ProcessEntry entry = processManager.assertProcess(instanceId);
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        checkpointManager.assertProcessAccess(entry);

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

        // TODO replace with ProcessKeyCache
        ProcessEntry entry = processManager.assertProcess(instanceId);
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        processManager.restoreFromCheckpoint(processKey, checkpointId);

        return new ResumeProcessResponse();
    }

    @POST
    @javax.ws.rs.Path("{id}/checkpoint")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public void uploadCheckpoint(@PathParam("id") UUID instanceId,
                                 @ApiParam MultipartInput input) {

        // TODO replace with ProcessKeyCache
        ProcessEntry entry = processManager.assertProcess(instanceId);
        ProcessKey processKey = new ProcessKey(entry.instanceId(), entry.createdAt());

        UUID checkpointId = MultipartUtils.assertUuid(input, "id");
        UUID correlationId = MultipartUtils.assertUuid(input, "correlationId");
        String checkpointName = MultipartUtils.assertString(input, "name");
        try (InputStream data = MultipartUtils.assertStream(input, "data");
             TemporaryPath tmpIn = IOUtils.tempFile("checkpoint", ".zip")) {

            Files.copy(data, tmpIn.path(), StandardCopyOption.REPLACE_EXISTING);
            checkpointManager.importCheckpoint(processKey, checkpointId, correlationId, checkpointName, tmpIn.path());
        } catch (ValidationErrorsException e) {
            throw new ConcordApplicationException(e.getMessage(), Response.Status.BAD_REQUEST);
        } catch (IOException e) {
            log.error("uploadCheckpoint ['{}'] -> error", processKey, e);
            throw new ConcordApplicationException("upload error: " + e.getMessage());
        }

        log.info("uploadCheckpoint ['{}'] -> done", processKey);
    }
}
