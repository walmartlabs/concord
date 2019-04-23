package com.walmartlabs.concord.agentoperator;

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

import com.walmartlabs.concord.agentoperator.crd.AgentPool;
import com.walmartlabs.concord.agentoperator.scheduler.Event;
import com.walmartlabs.concord.agentoperator.scheduler.Scheduler;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClusterWatcher implements Watcher<AgentPool> {

    private static final Logger log = LoggerFactory.getLogger(ClusterWatcher.class);

    private final Scheduler scheduler;

    public ClusterWatcher(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void eventReceived(Action action, AgentPool resource) {
        String resourceName = resource.getMetadata().getName();
        log.info("eventReceived -> action={}, resourceName={}", action, resourceName);
        scheduler.onEvent(actionToEvent(action), resource);
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        System.out.println("onClose: cause=" + cause);
    }

    private static final Event.Type actionToEvent(Action action) {
        switch (action) {
            case ADDED:
            case MODIFIED: {
                return Event.Type.MODIFIED;
            }
            case DELETED: {
                return Event.Type.DELETED;
            }
            default:
                throw new IllegalArgumentException("Unknown action type: " + action);
        }
    }
}
