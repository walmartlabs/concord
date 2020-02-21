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

import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.process.queue.ProcessQueueEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handles "exclusive" processes.
 * Exclusive processes can't be executed when there is another process
 * running in the same project.
 */
@Named
public class ExclusiveProcessFilter extends WaitProcessFinishFilter {

    private static final String WAIT_MODE = "wait";

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
        if (item.projectId() == null) {
            return Collections.emptyList();
        }

        boolean isWaitMode = WAIT_MODE.equals(MapUtils.getString(item.exclusive(), "mode"));
        String group = getGroup(item);
        if (group == null || !isWaitMode) {
            return Collections.emptyList();
        }

        List<UUID> result = new ArrayList<>(dao.findProcess(tx, item, group));
        for (ProcessQueueEntry p : startingProcesses) {
            if (item.projectId().equals(p.projectId()) && group.equals(getGroup(p))) {
                result.add(p.key().getInstanceId());
            }
        }
        return result;
    }

    private static String getGroup(ProcessQueueEntry entry) {
        return MapUtils.getString(entry.exclusive(), "group");
    }

    @Override
    protected String getReason() {
        return "exclusive process";
    }
}
