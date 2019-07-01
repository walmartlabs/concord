package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.org.policy.PolicyRules;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;
import org.jooq.Record1;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * Handles "max concurrent processes" policy.
 * The process won't be scheduled for execution until the number of currently running
 * processes in the same project exceeds the configured value.
 */
@Named
public class ConcurrentProcessFilter extends WaitProcessFinishFilter {

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    private static final Set<ProcessStatus> FINAL_STATUSES = ImmutableSet.of(
            ProcessStatus.SUSPENDED,
            ProcessStatus.FINISHED,
            ProcessStatus.FAILED,
            ProcessStatus.CANCELLED,
            ProcessStatus.TIMED_OUT);

    private final PolicyDao policyDao;

    @Inject
    public ConcurrentProcessFilter(PolicyDao policyDao, ProcessQueueDao processQueueDao) {
        super(processQueueDao);
        this.policyDao = policyDao;
    }

    @Override
    protected List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item) {
        PolicyEngine pe = getPolicyEngine(tx, item.orgId(), item.projectId(), item.initiatorId(), item.parentInstanceId());
        if (pe == null) {
            return Collections.emptyList();
        }

        CheckResult<ConcurrentProcessRule, List<UUID>> result = pe.getConcurrentProcessPolicy().check(
                () -> processesPerOrg(tx, item.orgId()),
                () -> processesPerProject(tx, item.projectId()));

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

    private PolicyEngine getPolicyEngine(DSLContext tx, UUID orgId, UUID prjId, UUID userId, UUID parentInstanceId) {
        if (prjId == null) {
            return null;
        }

        if (parentInstanceId != null) {
            return null;
        }

        PolicyRules policy = policyDao.getRules(tx, orgId, prjId, userId);
        if (policy == null) {
            return null;
        }

        // TODO caching? use a global singleton?
        return new PolicyEngine(policy.rules());
    }

    private List<UUID> processesPerOrg(DSLContext tx, UUID orgId) {
        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        Projects p = Projects.PROJECTS.as("p");
        return tx.select(q.INSTANCE_ID)
                .from(q)
                .innerJoin(p).on(q.PROJECT_ID.eq(p.PROJECT_ID))
                .where(p.ORG_ID.eq(orgId)
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)))
                .fetch(Record1::value1);
    }

    private List<UUID> processesPerProject(DSLContext tx, UUID projectId) {
        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        return tx.select(q.INSTANCE_ID)
                .from(q)
                .where(q.PROJECT_ID.eq(projectId)
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)))
                .fetch(Record1::value1);
    }
}
