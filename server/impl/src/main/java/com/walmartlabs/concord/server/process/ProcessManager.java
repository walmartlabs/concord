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

import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.pipelines.ForkPipeline;
import com.walmartlabs.concord.server.process.pipelines.NewProcessPipeline;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.state.ProcessCheckpointManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response.Status;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.agent.AgentManager.KeyAndAgent;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;
    private final AgentManager agentManager;
    private final ProcessLogManager logManager;
    private final ProjectAccessManager projectAccessManager;
    private final ProcessCheckpointManager checkpointManager;
    private final PayloadManager payloadManager;
    private final RepositoryDao repositoryDao;
    private final ProcessQueueManager queueManager;
    private final AuditLog auditLog;

    private final Chain processPipeline;
    private final Chain resumePipeline;
    private final Chain forkPipeline;

    private static final List<ProcessStatus> SERVER_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.NEW,
            ProcessStatus.PREPARING,
            ProcessStatus.ENQUEUED,
            ProcessStatus.WAITING,
            ProcessStatus.SUSPENDED);

    private static final List<ProcessStatus> TERMINATED_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.CANCELLED,
            ProcessStatus.FAILED,
            ProcessStatus.FINISHED,
            ProcessStatus.TIMED_OUT);

    private static final List<ProcessStatus> AGENT_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    private static final Set<ProcessStatus> RESTORE_ALLOWED_STATUSES = new HashSet<>(Arrays.asList(
            ProcessStatus.FAILED,
            ProcessStatus.FINISHED,
            ProcessStatus.TIMED_OUT,
            ProcessStatus.CANCELLED));

    @Inject
    public ProcessManager(ProcessQueueDao queueDao,
                          ProcessStateManager stateManager,
                          AgentManager agentManager,
                          ProcessLogManager logManager,
                          ProjectAccessManager projectAccessManager,
                          ProcessCheckpointManager checkpointManager,
                          PayloadManager payloadManager,
                          RepositoryDao repositoryDao,
                          ProcessQueueManager queueManager,
                          AuditLog auditLog,
                          NewProcessPipeline processPipeline,
                          ResumePipeline resumePipeline,
                          ForkPipeline forkPipeline) {

        this.queueDao = queueDao;
        this.stateManager = stateManager;
        this.agentManager = agentManager;
        this.logManager = logManager;
        this.queueManager = queueManager;
        this.projectAccessManager = projectAccessManager;
        this.checkpointManager = checkpointManager;
        this.payloadManager = payloadManager;
        this.repositoryDao = repositoryDao;
        this.auditLog = auditLog;

        this.processPipeline = processPipeline;
        this.resumePipeline = resumePipeline;
        this.forkPipeline = forkPipeline;
    }

    public ProcessResult start(Payload payload) {
        return start(processPipeline, payload);
    }

    public ProcessResult startFork(Payload payload) {
        return start(forkPipeline, payload);
    }

    public void restart(ProcessKey processKey) {
        ProcessKey rootProcessKey = queueDao.getRootId(processKey);
        if (rootProcessKey == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey, Status.NOT_FOUND);
        }

        ProcessEntry e = queueDao.get(rootProcessKey);
        if (e == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey, Status.NOT_FOUND);
        }

        // TODO: rename to assertProcessOperationRights or something
        assertKillOrDisableOrRestartRights(e);

        ProcessStatus s = e.status();
        if (!TERMINATED_PROCESS_STATUSES.contains(s)) {
            throw new ProcessException(rootProcessKey, "Can't restart running process: " + processKey, Status.CONFLICT);
        }

        // put new attemptNO somewhere

        queueDao.tx(tx -> {
            boolean updated = queueManager.updateExpectedStatus(tx, rootProcessKey, e.status(), ProcessStatus.NEW);
            if (updated) {
                List<ProcessKey> allProcesses = queueDao.getCascade(tx, rootProcessKey)
                        .stream()
                        .filter(p -> !p.equals(rootProcessKey))
                        .toList();
                kill(tx, allProcesses);

                stateManager.delete(tx, rootProcessKey);
            }
        });
    }

    public void resume(Payload payload) {
        log.info("resume ['{}']", payload.getProcessKey());
        resumePipeline.process(payload);
        log.info("resume ['{}'] -> done", payload.getProcessKey());
    }

    public void disable(ProcessKey processKey, boolean disabled) {
        ProcessEntry e = queueDao.get(processKey);
        if (e == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey, Status.NOT_FOUND);
        }

        assertKillOrDisableOrRestartRights(e);

        ProcessStatus s = e.status();
        if (TERMINATED_PROCESS_STATUSES.contains(s)) {
            queueDao.disable(processKey, disabled);
        }
    }

    public void kill(ProcessKey processKey) {
        queueDao.tx(tx -> kill(tx, processKey));
    }

    public void kill(DSLContext tx, ProcessKey processKey) {
        ProcessEntry process = assertProcess(tx, processKey);

        assertKillOrDisableOrRestartRights(process);

        boolean cancelled = false;
        while (!cancelled) {
            if (TERMINATED_PROCESS_STATUSES.contains(process.status())) {
                return;
            }

            boolean isServerProcess = SERVER_PROCESS_STATUSES.contains(process.status());
            if (isServerProcess) {
                cancelled = queueManager.updateExpectedStatus(tx, processKey, process.status(), ProcessStatus.CANCELLED);
            }

            if (!cancelled && process.lastAgentId() != null) {
                agentManager.killProcess(tx, processKey, process.lastAgentId());
                cancelled = true;
            }

            if (cancelled) {
                auditLogOnCancelled(process);
            } else {
                process = assertProcess(tx, processKey);
            }
        }
    }

    public void kill(DSLContext tx, List<ProcessKey> processKeys) {
        List<ProcessKey> keys = new ArrayList<>(processKeys);
        while(!keys.isEmpty()) {
            // TODO: better way
            List<ProcessEntry> processes = keys.stream()
                    .map(k -> queueDao.get(tx, k, Collections.emptySet()))
                    .collect(Collectors.toList());

            List<ProcessEntry> terminatedProcesses = filterProcesses(processes, TERMINATED_PROCESS_STATUSES);
            terminatedProcesses.forEach(p -> keys.remove(new ProcessKey(p.instanceId(), p.createdAt())));

            List<ProcessEntry> serverProcesses = filterProcesses(processes, SERVER_PROCESS_STATUSES);
            if (!serverProcesses.isEmpty()) {
                List<ProcessKey> serverProcessKeys = serverProcesses.stream()
                        .map(p -> new ProcessKey(p.instanceId(), p.createdAt()))
                        .collect(Collectors.toList());

                List<ProcessKey> updated = queueManager.updateExpectedStatus(tx, serverProcessKeys, SERVER_PROCESS_STATUSES, ProcessStatus.CANCELLED);
                serverProcesses.stream()
                        .filter(p -> updated.contains(new ProcessKey(p.instanceId(), p.createdAt())))
                        .forEach(this::auditLogOnCancelled);

                keys.removeAll(updated);
            }

            List<ProcessEntry> agentProcesses = filterProcesses(processes, AGENT_PROCESS_STATUSES);
            if (!agentProcesses.isEmpty()) {
                agentManager.killProcess(agentProcesses.stream().map(p -> new KeyAndAgent(new ProcessKey(p.instanceId(), p.createdAt()), p.lastAgentId())).collect(Collectors.toList()));

                agentProcesses.forEach(this::auditLogOnCancelled);

                agentProcesses.forEach(p -> keys.remove(new ProcessKey(p.instanceId(), p.createdAt())));
            }
        }
    }

    public void killCascade(PartialProcessKey processKey) {
        ProcessEntry e = queueManager.get(processKey);
        if (e == null) {
            throw new ProcessException(processKey, "Process not found: " + processKey, Status.NOT_FOUND);
        }

        assertKillOrDisableOrRestartRights(e);

        List<ProcessKey> allProcesses = queueDao.getCascade(processKey);
        queueDao.tx(tx -> kill(tx, allProcesses));
    }

    public void restoreFromCheckpoint(ProcessKey processKey, UUID checkpointId) {
        ProcessEntry entry = queueDao.get(processKey);

        checkpointManager.assertProcessAccess(entry);

        if (checkpointId == null) {
            throw new ConcordApplicationException("'checkpointId' is mandatory");
        }

        if (entry.disabled()) {
            throw new ConcordApplicationException("Checkpoint can not be restored as process is disabled -> " + entry.instanceId());
        }

        ProcessStatus s = entry.status();
        if (!RESTORE_ALLOWED_STATUSES.contains(s)) {
            throw new ConcordApplicationException("Unable to restore a checkpoint, the process is " + s);
        }

        ProcessCheckpointManager.CheckpointInfo checkpointInfo = checkpointManager.restoreCheckpoint(processKey, checkpointId);
        if (checkpointInfo == null) {
            throw new ConcordApplicationException("Checkpoint " + checkpointId + " not found");
        }

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, checkpointInfo.eventName(), null);
        } catch (IOException e) {
            log.error("restore ['{}', '{}'] -> error creating a payload: {}", processKey, checkpointInfo.name(), e);
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        queueManager.restore(processKey, checkpointId, entry.status());

        logManager.info(processKey, "Restoring from checkpoint '{}'", checkpointInfo.name());

        resume(payload);
    }

    public void updateStatus(ProcessKey processKey, String agentId, ProcessStatus status) {
        assertUpdateRights(processKey);

        // TODO determine the correct status on the agent?
        if (status == ProcessStatus.FINISHED && isSuspended(processKey)) {
            status = ProcessStatus.SUSPENDED;
        }

        if (status == ProcessStatus.CANCELLED && isFinished(processKey)) {
            log.info("updateStatus [{}, '{}', {}] -> ignored, process finished", processKey, agentId, status);
            return;
        }

        queueManager.updateAgentId(processKey, agentId, status);
        logManager.info(processKey, "Process status: {}", status);

        log.info("updateStatus [{}, '{}', {}] -> done", processKey, agentId, status);
    }

    public ProcessEntry assertProcess(UUID instanceId) {
        ProcessEntry p = queueManager.get(PartialProcessKey.from(instanceId));
        if (p == null) {
            throw new ConcordApplicationException("Process instance not found", Status.NOT_FOUND);
        }
        return p;
    }

    public void assertResumeEvents(ProcessKey processKey, Set<String> events) {
        if (events.isEmpty()) {
            throw new ConcordApplicationException("Empty resume events", Status.BAD_REQUEST);
        }

        Set<String> expectedEvents = getResumeEvents(processKey);

        Set<String> unexpectedEvents = new HashSet<>(events);
        unexpectedEvents.removeAll(expectedEvents);

        if (!unexpectedEvents.isEmpty()) {
            logManager.warn(processKey, "Unexpected 'resume' events: {}, expected: {}", unexpectedEvents, expectedEvents);
            throw new ConcordApplicationException("Unexpected 'resume' events: " + unexpectedEvents, Status.BAD_REQUEST);
        }
    }

    public void updateExclusive(DSLContext tx, ProcessKey processKey, ExclusiveMode exclusive) {
        queueDao.updateExclusive(tx, processKey, exclusive);
    }

    private ProcessEntry assertProcess(DSLContext tx, ProcessKey processKey) {
        ProcessEntry process = queueDao.get(tx, processKey, Collections.emptySet());
        if (process != null) {
            return process;
        }
        throw new ProcessException(processKey, "Process not found: " + processKey, Status.NOT_FOUND);
    }

    private boolean isSuspended(ProcessKey processKey) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.exists(processKey, resource);
    }

    private boolean isFinished(PartialProcessKey processKey) {
        ProcessStatus status = queueDao.getStatus(processKey);
        if (status == null) {
            return true;
        }

        return TERMINATED_PROCESS_STATUSES.contains(status);
    }

    private ProcessResult start(Chain pipeline, Payload payload) {
        assertRepositoryDisabled(payload);

        ProcessKey processKey = payload.getProcessKey();

        try {
            pipeline.process(payload);
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            log.error("start ['{}'] -> error starting the process", processKey, e);
            throw new ProcessException(processKey, "Error starting the process", e, Status.INTERNAL_SERVER_ERROR);
        }

        UUID instanceId = processKey.getInstanceId();
        return new ProcessResult(instanceId);
    }

    private void assertRepositoryDisabled(Payload payload) {
        UUID repoId = payload.getHeader(Payload.REPOSITORY_ID);
        if (repoId == null) {
            return;
        }

        RepositoryEntry repo = repositoryDao.get(repoId);
        if (repo.isDisabled()) {
            throw new ConcordApplicationException("Repository is disabled -> " + repo.getName());
        }
    }

    private void assertKillOrDisableOrRestartRights(ProcessEntry e) {
        if (Roles.isAdmin()) {
            return;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.getId().equals(e.initiatorId())) {
            // process owners can kill or disable their own processes
            return;
        }

        UUID projectId = e.projectId();
        if (projectId != null) {
            // only org members with WRITER rights can kill/disable/restart the process
            projectAccessManager.assertAccess(projectId, ResourceAccessLevel.WRITER, true);
            return;
        }

        throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have permissions " +
                "to kill or disable the process: " + e.instanceId());
    }

    private void assertUpdateRights(PartialProcessKey processKey) {
        if (Roles.isAdmin() || Roles.isGlobalWriter()) {
            return;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        SessionKeyPrincipal s = SessionKeyPrincipal.getCurrent();
        if (s != null && processKey.partOf(s.getProcessKey())) {
            // processes can update their own statuses
            return;
        }

        throw new UnauthorizedException("The current user (" + p.getUsername() + ") does not have permissions " +
                "to update the process status: " + processKey);
    }

    public void auditLogOnCancelled(ProcessEntry p) {
        auditLog.add(AuditObject.PROCESS, AuditAction.DELETE)
                .field("instanceId", p.instanceId())
                .field("status", p.status())
                .field("orgId", p.orgId())
                .field("projectId", p.projectId())
                .log();
    }

    private Set<String> getResumeEvents(ProcessKey processKey) {
        String path = ProcessStateManager.path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.get(processKey, path, ProcessManager::deserialize)
                .orElse(Set.of());
    }

    private static Optional<Set<String>> deserialize(InputStream in) {
        Set<String> result = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
            return Optional.of(result);
        } catch (IOException e) {
            throw new RuntimeException("Error while deserializing a resume events: " + e.getMessage(), e);
        }
    }

    private static List<ProcessEntry> filterProcesses(List<ProcessEntry> l, List<ProcessStatus> expected) {
        return l.stream()
                .filter(r -> expected.contains(r.status()))
                .collect(Collectors.toList());
    }

    public static final class ProcessResult implements Serializable {

        private static final long serialVersionUID = 1L;

        private final UUID instanceId;

        public ProcessResult(UUID instanceId) {
            this.instanceId = instanceId;
        }

        public UUID getInstanceId() {
            return instanceId;
        }
    }
}
