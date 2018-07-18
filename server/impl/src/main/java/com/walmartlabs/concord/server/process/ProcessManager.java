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
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.ConcordFormService.FormSubmitResult;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.pipelines.ForkPipeline;
import com.walmartlabs.concord.server.process.pipelines.ProcessPipeline;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import org.apache.shiro.authz.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;
    private final AgentManager agentManager;
    private final LogManager logManager;
    private final ConcordFormService formService;
    private final ProjectAccessManager projectAccessManager;

    private final Chain processPipeline;
    private final Chain resumePipeline;
    private final Chain forkPipeline;

    private final ObjectMapper objectMapper;

    private static final List<ProcessStatus> SERVER_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.ENQUEUED,
            ProcessStatus.PREPARING,
            ProcessStatus.SUSPENDED);
    private static final List<ProcessStatus> TERMINATED_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.CANCELLED,
            ProcessStatus.FAILED,
            ProcessStatus.FINISHED);
    private static final List<ProcessStatus> AGENT_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    @Inject
    public ProcessManager(ProcessQueueDao queueDao,
                          ProcessStateManager stateManager,
                          AgentManager agentManager,
                          LogManager logManager,
                          ConcordFormService formService,
                          ProjectAccessManager projectAccessManager,
                          ProcessPipeline processPipeline,
                          ResumePipeline resumePipeline,
                          ForkPipeline forkPipeline) {

        this.queueDao = queueDao;
        this.stateManager = stateManager;
        this.agentManager = agentManager;
        this.logManager = logManager;
        this.formService = formService;
        this.projectAccessManager = projectAccessManager;

        this.processPipeline = processPipeline;
        this.resumePipeline = resumePipeline;
        this.forkPipeline = forkPipeline;

        this.objectMapper = new ObjectMapper();
    }

    public ProcessEntry nextPayload(Map<String, Object> capabilities) {
        return queueDao.poll(capabilities);
    }

    public ProcessResult start(Payload payload, boolean sync) {
        return start(processPipeline, payload, sync);
    }

    public ProcessResult startFork(Payload payload, boolean sync) {
        return start(forkPipeline, payload, sync);
    }

    public void resume(Payload payload) {
        resumePipeline.process(payload);
    }

    public void kill(UUID instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            throw new ProcessException(null, "Process not found: " + instanceId, Status.NOT_FOUND);
        }

        assertKillRights(e);

        ProcessStatus s = e.getStatus();
        if (TERMINATED_PROCESS_STATUSES.contains(s)) {
            return;
        }

        if (cancel(instanceId, s, SERVER_PROCESS_STATUSES)) {
            return;
        }

        agentManager.killProcess(instanceId);
    }

    public void killCascade(UUID instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            throw new ProcessException(null, "Process not found: " + instanceId, Status.NOT_FOUND);
        }

        assertKillRights(e);

        List<ProcessEntry> l = null;
        boolean updated = false;
        while (!updated) {
            l = queueDao.getCascade(instanceId);
            List<UUID> ids = filterProcessIds(l, SERVER_PROCESS_STATUSES);
            updated = ids.isEmpty() || queueDao.update(ids, ProcessStatus.CANCELLED, SERVER_PROCESS_STATUSES);
        }

        List<UUID> ids = filterProcessIds(l, AGENT_PROCESS_STATUSES);
        if (!ids.isEmpty()) {
            agentManager.killProcess(ids);
        }
    }

    public void updateStatus(UUID instanceId, String agentId, ProcessStatus status) {
        assertUpdateRights(instanceId);

        if (status == ProcessStatus.FINISHED && isSuspended(instanceId)) {
            status = ProcessStatus.SUSPENDED;
        }

        queueDao.updateAgentId(instanceId, agentId, status);
        logManager.info(instanceId, "Process status: {}", status);

        log.info("updateStatus [{}, '{}', {}] -> done", instanceId, agentId, status);
    }

    private boolean isSuspended(UUID instanceId) {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.exists(instanceId, resource);
    }

    private ProcessResult start(Chain pipeline, Payload payload, boolean sync) {
        UUID instanceId = payload.getInstanceId();

        try {
            pipeline.process(payload);
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            log.error("start ['{}'] -> error starting the process", instanceId, e);
            throw new ProcessException(instanceId, "Error starting the process", e, Status.INTERNAL_SERVER_ERROR);
        }

        Map<String, Object> out = null;
        if (sync) {
            Map<String, Object> args = readArgs(instanceId);
            out = process(instanceId, args);
        }

        return new ProcessResult(instanceId, out);
    }

    private Map<String, Object> process(UUID instanceId, Map<String, Object> params) {
        while (true) {
            ProcessEntry psr = queueDao.get(instanceId);
            ProcessStatus status = psr.getStatus();

            if (status == ProcessStatus.SUSPENDED) {
                wakeUpProcess(instanceId, params);
            } else if (status == ProcessStatus.FAILED || status == ProcessStatus.CANCELLED) {
                throw new ProcessException(instanceId, "Process error: " + status, Status.INTERNAL_SERVER_ERROR);
            } else if (status == ProcessStatus.FINISHED) {
                return readOutValues(instanceId);
            }

            try {
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void wakeUpProcess(UUID instanceId, Map<String, Object> data) {
        FormSubmitResult r = formService.submitNext(instanceId, data);
        if (r != null && !r.isValid()) {
            String error = "n/a";
            if (r.getErrors() != null) {
                error = r.getErrors().stream().map(e -> e.getFieldName() + ": " + e.getError()).collect(Collectors.joining(","));
            }
            throw new ProcessException(instanceId, "Form '" + r.getFormName() + "' submit error: " + error, Status.BAD_REQUEST);
        }
    }

    private boolean cancel(UUID instanceId, ProcessStatus current, List<ProcessStatus> expected) {
        boolean found = false;
        for (ProcessStatus s : expected) {
            if (current == s) {
                found = true;
                break;
            }
        }

        return found && queueDao.update(instanceId, current, ProcessStatus.CANCELLED);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readOutValues(UUID instanceId) {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME, InternalConstants.Files.OUT_VALUES_FILE_NAME);

        Optional<Map<String, Object>> o = stateManager.get(instanceId, resource, in -> {
            try {
                return Optional.of(objectMapper.readValue(in, Map.class));
            } catch (IOException e) {
                throw new ProcessException(instanceId, "Error while reading OUT variables data", e);
            }
        });

        return o.orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArgs(UUID instanceId) {
        String resource = InternalConstants.Files.REQUEST_DATA_FILE_NAME;
        Optional<Map<String, Object>> o = stateManager.get(instanceId, resource, in -> {
            try {
                ObjectMapper om = new ObjectMapper();

                Map<String, Object> cfg = om.readValue(in, Map.class);
                Map<String, Object> args = (Map<String, Object>) cfg.get(InternalConstants.Request.ARGUMENTS_KEY);

                return Optional.ofNullable(args);
            } catch (IOException e) {
                throw new ConcordApplicationException("Error while reading request data", e);
            }
        });
        return o.orElse(Collections.emptyMap());
    }

    private void assertKillRights(ProcessEntry e) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            return;
        }

        if (p.getUsername().equals(e.getInitiator())) {
            // process owners can kill their own processes
            return;
        }

        if (e.getProjectId() != null) {
            // only org members with WRITER rights can kill the process
            projectAccessManager.assertProjectAccess(e.getProjectId(), ResourceAccessLevel.WRITER, true);
        }

        throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have permissions " +
                "to kill the process: " + e.getInstanceId());
    }

    public void assertUpdateRights(UUID instanceId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin() || p.isGlobalWriter()) {
            return;
        }

        SessionKeyPrincipal s = SessionKeyPrincipal.getCurrent();
        if (s != null && instanceId.equals(s.getProcessInstanceId())) {
            // processes can update their own statuses
            return;
        }

        throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have permissions " +
                "to update the process status: " + instanceId);
    }

    private static List<UUID> filterProcessIds(List<ProcessEntry> l, List<ProcessStatus> expected) {
        return l.stream()
                .filter(r -> expected.contains(r.getStatus()))
                .map(ProcessEntry::getInstanceId)
                .collect(Collectors.toList());
    }

    public static final class ProcessResult implements Serializable {

        private final UUID instanceId;
        private final Map<String, Object> out;

        public ProcessResult(UUID instanceId, Map<String, Object> out) {
            this.instanceId = instanceId;
            this.out = out;
        }

        public UUID getInstanceId() {
            return instanceId;
        }

        public Map<String, Object> getOut() {
            return out;
        }
    }
}
