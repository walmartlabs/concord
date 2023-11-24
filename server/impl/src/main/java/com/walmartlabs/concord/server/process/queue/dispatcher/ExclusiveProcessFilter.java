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

import com.walmartlabs.concord.runtime.v2.model.ExclusiveMode;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.waits.ProcessWaitManager;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.*;

/**
 * Handles "exclusive" processes.
 * Exclusive processes can't be executed when there is another process
 * running in the same project and group.
 */
public class ExclusiveProcessFilter extends WaitProcessFinishFilter {

    private final ExclusiveProcessFilterDao dao;

    @Inject
    public ExclusiveProcessFilter(ProcessWaitManager processWaitManager, ProcessQueueManager processQueueManager, ExclusiveProcessFilterDao dao) {
        super(processWaitManager, processQueueManager);
        this.dao = dao;
    }

    @Override
    public void cleanup() {
        dao.cleanup();
    }

    @Override
    protected List<UUID> findProcess(DSLContext tx, ProcessQueueEntry item, List<ProcessQueueEntry> startingProcesses) {
        UUID projectId = item.projectId();
        ExclusiveMode exclusive = item.exclusive();

        if (projectId == null || exclusive == null) {
            return Collections.emptyList();
        }

        boolean isWaitMode = exclusive.mode() == ExclusiveMode.Mode.wait;
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

    private static boolean groupEquals(ExclusiveMode a, ExclusiveMode b) {
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
