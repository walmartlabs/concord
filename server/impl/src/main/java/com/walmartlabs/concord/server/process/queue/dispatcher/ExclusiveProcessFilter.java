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

import com.walmartlabs.concord.runtime.v2.model.ExclusiveModeConfiguration;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

/**
 * Handles "exclusive" processes.
 * Exclusive processes can't be executed when there is another process
 * running in the same project.
 */
@Named
public class ExclusiveProcessFilter extends WaitProcessFinishFilter {

    private final ExclusiveProcessFilterDao dao;

    @Inject
    public ExclusiveProcessFilter(ProcessQueueManager processQueueManager, ExclusiveProcessFilterDao dao) {
        super(processQueueManager);
        this.dao = dao;
    }

    @Override
    public void cleanup() {
        dao.cleanup();
    }

    @Override
    protected List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item, List<ProcessQueueEntry> startingProcesses) {
        if (item.projectId() == null || item.exclusive() == null) {
            return Collections.emptyList();
        }

        UUID projectId = Objects.requireNonNull(item.projectId());
        ExclusiveModeConfiguration exclusive = Objects.requireNonNull(item.exclusive());

        boolean isWaitMode = exclusive.mode() == ExclusiveModeConfiguration.Mode.wait;
        if (!isWaitMode) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(dao.findProcess(tx, item, exclusive.group()));
        for (ProcessQueueEntry p : startingProcesses) {
            if (projectId.equals(p.projectId()) && groupEquals(exclusive, p.exclusive())) {
                result.add(p.key().getInstanceId());
            }
        }
        return result;
    }

    private static boolean groupEquals(ExclusiveModeConfiguration a, ExclusiveModeConfiguration b) {
        if (a == null || b == null) {
            return false;
        }

        return Objects.equals(a.group(), b.group());
    }

    @Override
    protected String getReason() {
        return "exclusive process";
    }
}
