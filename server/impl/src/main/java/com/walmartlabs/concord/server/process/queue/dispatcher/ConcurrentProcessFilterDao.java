package com.walmartlabs.concord.server.process.queue.dispatcher;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.jooq.tables.ProcessQueue;
import com.walmartlabs.concord.server.jooq.tables.Projects;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import org.jooq.DSLContext;
import org.jooq.Record1;

import java.util.*;

public class ConcurrentProcessFilterDao {

    private static final List<ProcessStatus> RUNNING_PROCESS_STATUSES = Arrays.asList(
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.RESUMING);

    private final Map<UUID, List<UUID>> perOrg = new HashMap<>();
    private final Map<UUID, List<UUID>> perProject = new HashMap<>();

    public List<UUID> processesPerOrg(DSLContext tx, UUID orgId) {
        return perOrg.computeIfAbsent(orgId, id -> computeProcessesPerOrg(tx, id));
    }

    public List<UUID> processesPerProject(DSLContext tx, UUID projectId) {
        return perProject.computeIfAbsent(projectId, id -> computeProcessesPerProject(tx, id));
    }

    public void cleanup() {
        perOrg.clear();
        perProject.clear();
    }

    private List<UUID> computeProcessesPerOrg(DSLContext tx, UUID orgId) {
        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        Projects p = Projects.PROJECTS.as("p");
        return tx.select(q.INSTANCE_ID)
                .from(q)
                .innerJoin(p).on(q.PROJECT_ID.eq(p.PROJECT_ID))
                .where(p.ORG_ID.eq(orgId)
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)))
                .fetch(Record1::value1);
    }

    private static List<UUID> computeProcessesPerProject(DSLContext tx, UUID projectId) {
        ProcessQueue q = ProcessQueue.PROCESS_QUEUE.as("q");
        return tx.select(q.INSTANCE_ID)
                .from(q)
                .where(q.PROJECT_ID.eq(projectId)
                        .and(q.CURRENT_STATUS.in(RUNNING_PROCESS_STATUSES)))
                .fetch(Record1::value1);
    }
}
