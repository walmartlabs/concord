package com.walmartlabs.concord.agentoperator.scheduler;

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
import com.walmartlabs.concord.agentoperator.crd.AgentPoolConfiguration;
import com.walmartlabs.concord.agentoperator.planner.Change;
import com.walmartlabs.concord.agentoperator.planner.Planner;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueClient;
import com.walmartlabs.concord.agentoperator.processqueue.ProcessQueueEntry;
import com.walmartlabs.concord.agentoperator.resources.AgentPod;
import com.walmartlabs.concord.common.ConfigurationUtils;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

public class Scheduler {

    private static final Logger log = LoggerFactory.getLogger(Scheduler.class);

    private static final long POLL_DELAY = 5000;
    private static final long ERROR_DELAY = 10000;

    private final KubernetesClient k8sClient;
    private final ProcessQueueClient processQueueClient;
    private final Planner planner;
    private final Map<String, AgentPoolInstance> pools;
    private final List<Event> events;

    public Scheduler(KubernetesClient k8sClient, Configuration cfg) {
        this.k8sClient = k8sClient;
        this.processQueueClient = new ProcessQueueClient(cfg.concordBaseUrl, cfg.concordApiToken);
        this.planner = new Planner(k8sClient);
        this.pools = new HashMap<>();
        this.events = new LinkedList<>();
    }

    public void onEvent(Event.Type type, AgentPool resource) {
        synchronized (events) {
            events.add(new Event(type, resource));
        }
    }

    public void start() {
        new Thread(new Worker(), "scheduler-worker").start();
    }

    /**
     * Process the recent events and update the cluster state.
     */
    private void doRun() throws Exception {
        // drain the event queue
        List<Event> evs;
        synchronized (events) {
            evs = new ArrayList<>(events);
            events.clear();
        }

        for (Event e : evs) {
            AgentPool resource = e.getResource();
            String resourceName = resource.getMetadata().getName();

            switch (e.getType()) {
                case MODIFIED: {
                    onAdd(resourceName, resource);
                    break;
                }
                case DELETED: {
                    onDelete(resourceName);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown event type: " + e.getType());
            }
        }

        // process the pool
        List<AgentPoolInstance> todo;
        synchronized (pools) {
            todo = new ArrayList<>(pools.values());
        }

        if (todo.isEmpty()) {
            return;
        }

        // fetch the process queue status

        todo.parallelStream().forEach(i -> {
            try {
                switch (i.getStatus()) {
                    case ACTIVE: {
                        updateTargetSize(i);
                        processActive(i);
                        break;
                    }
                    case DELETED: {
                        processDeleted(i);
                        break;
                    }
                    default:
                        throw new IllegalArgumentException("Unknown pool status: " + i.getStatus());
                }
            } catch (IOException e) {
                log.error("doRun -> error while processing a registered pool {}: {}", i.getName(), e.getMessage());
            }
        });
    }

    private void onAdd(String resourceName, AgentPool resource) {
        int targetSize = resource.getSpec().getSize();
        synchronized (pools) {
            long currentTimeStamp = System.currentTimeMillis();
            pools.put(resourceName, new AgentPoolInstance(resourceName, resource, AgentPoolInstance.Status.ACTIVE,
                    targetSize, currentTimeStamp, currentTimeStamp, currentTimeStamp));
        }
    }

    private void onDelete(String resourceName) {
        synchronized (pools) {
            AgentPoolInstance i = pools.get(resourceName);
            if (i == null) {
                return;
            }

            pools.put(resourceName, AgentPoolInstance.updateStatus(i, AgentPoolInstance.Status.DELETED));
        }
    }

    private void updateTargetSize(AgentPoolInstance i) throws IOException {
        if (!i.getResource().getSpec().isAutoScale()) {
            return;
        }

        int queueQueryLimit = i.getResource().getSpec().getQueueQueryLimit();
        Map<String, Object> queueSelector = i.getResource().getSpec().getQueueSelector();
        String flavor = (String) ConfigurationUtils.get(queueSelector, "agent", "flavor");
        List<ProcessQueueEntry> queueEntries = processQueueClient.query("ENQUEUED", queueQueryLimit, flavor);

        AgentPoolConfiguration spec = i.getResource().getSpec();
        if (!spec.isAutoScale()) {
            return;
        }

        AutoScaler autoScaler = new AutoScaler(n -> AgentPod.list(k8sClient, n).size());
        synchronized (pools) {
            pools.put(i.getName(), autoScaler.apply(i, queueEntries));
        }
    }

    private void processActive(AgentPoolInstance i) throws IOException {
        List<Change> changes = planner.plan(i);
        apply(changes);
    }

    private void processDeleted(AgentPoolInstance i) throws IOException {
        String resourceName = i.getName();

        // remove all pool's pods
        List<Change> changes = planner.plan(i);
        apply(changes);

        // if no pods left - remove the pool
        List<Pod> pods = AgentPod.listPods(k8sClient, resourceName);
        if (pods.isEmpty()) {
            synchronized (pools) {
                pools.remove(resourceName);
                log.info("processDeleted ['{}'] -> no pods left, the pool was removed", resourceName);
            }
        } else {
            log.info("processDeleted ['{}'] -> {} pod(s) left, will be deleted on the next iteration", resourceName, pods.size());
        }
    }

    private void apply(List<Change> changes) {
        changes.forEach(c -> c.apply(k8sClient));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private class Worker implements Runnable {

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    doRun();
                    sleep(POLL_DELAY);
                } catch (Exception e) {
                    log.error("run -> error while running the scheduler: {}", e.getMessage(), e);
                    sleep(ERROR_DELAY);
                }
            }
        }
    }

    public static class Configuration {

        private final String concordBaseUrl;
        private final String concordApiToken;

        public Configuration(String concordBaseUrl, String concordApiToken) {
            this.concordBaseUrl = concordBaseUrl;
            this.concordApiToken = concordApiToken;
        }
    }
}
