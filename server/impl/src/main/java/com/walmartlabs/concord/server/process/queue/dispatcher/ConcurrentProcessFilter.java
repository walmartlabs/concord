package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.ConcurrentProcessRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.waits.ProcessCompletionCondition;
import com.walmartlabs.concord.server.process.waits.ProcessWaitManager;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.*;

/**
 * Handles "max concurrent processes" policy.
 * The process won't be scheduled for execution until the number of currently running
 * processes in the same project exceeds the configured value.
 */
public class ConcurrentProcessFilter extends WaitProcessFinishFilter {

    private static final Set<ProcessStatus> FINAL_STATUSES = ImmutableSet.of(
            ProcessStatus.SUSPENDED,
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.TIMED_OUT);

    private final ConcurrentProcessFilterDao dao;
    private final PolicyManager policyManager;

    @Inject
    public ConcurrentProcessFilter(ProcessWaitManager processWaitManager, PolicyManager policyManager, ProcessQueueManager processQueueManager, ConcurrentProcessFilterDao dao) {
        super(processWaitManager, processQueueManager);
        this.policyManager = policyManager;
        this.dao = dao;
    }

    @Override
    public void cleanup() {
        dao.cleanup();
    }

    @Override
    protected List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item, List<ProcessQueueEntry> startingProcesses) {
        PolicyEngine pe = getPolicyEngine(item.orgId(), item.projectId(), item.initiatorId());
        if (pe == null) {
            return Collections.emptyList();
        }

        CheckResult<ConcurrentProcessRule, List<UUID>> result = pe.getConcurrentProcessPolicy().check(
                () -> processesPerOrg(tx, item.orgId(), startingProcesses),
                () -> processesPerProject(tx, item.projectId(), startingProcesses));

        if (result.getDeny().isEmpty()) {
            return Collections.emptyList();
        }

        return result.getDeny().get(0).getEntity();
    }

    @Override
    protected String getReason() {
        return "max concurrent process limit exceeded";
    }

    @Override
    protected Set<ProcessStatus> getFinalStatuses() {
        return FINAL_STATUSES;
    }

    @Override
    protected ProcessCompletionCondition.CompleteCondition getCompleteCondition() {
        return ProcessCompletionCondition.CompleteCondition.ONE_OF;
    }

    private PolicyEngine getPolicyEngine(UUID orgId, UUID prjId, UUID userId) {
        if (prjId == null) {
            return null;
        }

        return policyManager.get(orgId, prjId, userId);
    }

    private List<UUID> processesPerOrg(DSLContext tx, UUID orgId, List<ProcessQueueEntry> startingProcesses) {
        if (orgId == null) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(dao.processesPerOrg(tx, orgId));
        for (ProcessQueueEntry p : startingProcesses) {
            if (orgId.equals(p.orgId())) {
                result.add(p.key().getInstanceId());
            }
        }
        return result;
    }

    private List<UUID> processesPerProject(DSLContext tx, UUID projectId, List<ProcessQueueEntry> startingProcesses) {
        if (projectId == null) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(dao.processesPerProject(tx, projectId));
        for (ProcessQueueEntry p : startingProcesses) {
            if (projectId.equals(p.projectId())) {
                result.add(p.key().getInstanceId());
            }
        }
        return result;
    }
}
