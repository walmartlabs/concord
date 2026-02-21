package com.walmartlabs.concord.server.plugins.noderoster.processor;

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

import com.walmartlabs.concord.common.StringUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.HostManager;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterProcessHosts;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.records.NodeRosterProcessHostsRecord;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.immutables.value.Value;
import org.jooq.BatchBindStep;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.*;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterProcessHosts.NODE_ROSTER_PROCESS_HOSTS;
import static org.jooq.impl.DSL.value;

/**
 * Saves all hosts found in events of a particular process.
 */
@Named
public class ProcessHostsProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(ProcessHostsProcessor.class);

    private final Dao dao;
    private final HostManager hosts;

    @Inject
    public ProcessHostsProcessor(Dao dao, HostManager hosts) {
        this.dao = dao;
        this.hosts = hosts;
    }

    @Override
    @WithTimer
    public void process(List<AnsibleEvent> events) {
        Set<ProcessHostItem> items = new HashSet<>();

        for (AnsibleEvent e : events) {
            String host = e.data().getHost();
            if (host != null) {
                items.add(ProcessHostItem.builder()
                        .instanceId(e.instanceId())
                        .instanceCreatedAt(e.instanceCreatedAt())
                        .host(hosts.getOrCreate(host))
                        .initiator(e.initiator())
                        .initiatorId(e.initiatorId())
                        .projectId(e.projectId())
                        .build());
            }
        }

        if (!items.isEmpty()) {
            dao.insert(items);
        }

        log.info("process -> events: {}, items: {}", events.size(), items.size());
    }

    @Named
    public static class Dao extends AbstractDao {

        private final ProcessHostsPartitioner partitioner;

        @Inject
        public Dao(@NodeRosterDB Configuration cfg, ProcessHostsPartitioner partitioner) {
            super(cfg);
            this.partitioner = partitioner;
        }

        @WithTimer
        public void insert(Set<ProcessHostItem> items) {
            tx(tx -> insert(tx, items));
        }

        private void insert(DSLContext tx, Set<ProcessHostItem> items) {
            NodeRosterProcessHosts h = NODE_ROSTER_PROCESS_HOSTS.as("ph");

            Map<Table<NodeRosterProcessHostsRecord>, Collection<ProcessHostItem>> tblItems = partitioner.process(items);
            for (Map.Entry<Table<NodeRosterProcessHostsRecord>, Collection<ProcessHostItem>> e : tblItems.entrySet()) {
                BatchBindStep q = tx.batch(tx.insertInto(e.getKey().as("ph"),
                        h.INSTANCE_ID,
                        h.INSTANCE_CREATED_AT,
                        h.HOST_ID,
                        h.INITIATOR,
                        h.INITIATOR_ID,
                        h.PROJECT_ID)
                        .values((UUID) null, null, null, null, null, null)
                        .onConflictDoNothing());

                for (ProcessHostItem i : e.getValue()) {
                    q.bind(value(i.instanceId()),
                            value(i.instanceCreatedAt()),
                            value(i.host()),
                            value(StringUtils.abbreviate(i.initiator(), h.INITIATOR.getDataType().length())),
                            value(i.initiatorId()),
                            value(i.projectId()));
                }

                q.execute();
            }
        }
    }

    @Named
    public static class ProcessHostsPartitioner extends Partitioner<ProcessHostItem, NodeRosterProcessHostsRecord> {

        public ProcessHostsPartitioner() {
            super(NODE_ROSTER_PROCESS_HOSTS, ProcessHostItem::instanceCreatedAt);
        }
    }

    @Value.Immutable
    public abstract static class ProcessHostItem {

        public abstract UUID instanceId();

        public abstract OffsetDateTime instanceCreatedAt();

        public abstract UUID host();

        @Nullable
        public abstract String initiator();

        @Nullable
        public abstract UUID initiatorId();

        @Nullable
        public abstract UUID projectId();

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcessHostItem that = (ProcessHostItem) o;
            return Objects.equals(instanceId(), that.instanceId()) &&
                    Objects.equals(instanceCreatedAt(), that.instanceCreatedAt()) &&
                    Objects.equals(host(), that.host());
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceId(), instanceCreatedAt(), host());
        }

        public static ImmutableProcessHostItem.Builder builder() {
            return ImmutableProcessHostItem.builder();
        }
    }
}
