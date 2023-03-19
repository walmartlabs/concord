package com.walmartlabs.concord.server.plugins.noderoster.dao;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.HostEntry;
import com.walmartlabs.concord.server.plugins.noderoster.HostFilter;
import com.walmartlabs.concord.server.plugins.noderoster.HostsDataInclude;
import com.walmartlabs.concord.server.plugins.noderoster.ProcessEntry;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHosts;
import com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterProcessHosts;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import org.jooq.Record;
import org.jooq.*;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.PROJECTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts.NODE_ROSTER_HOST_ARTIFACTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostFacts.NODE_ROSTER_HOST_FACTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHosts.NODE_ROSTER_HOSTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterProcessHosts.NODE_ROSTER_PROCESS_HOSTS;
import static org.jooq.impl.DSL.select;

public class HostsDao extends AbstractDao {

    private final ProcessKeyCache processKeyCache;

    @Inject
    public HostsDao(@NodeRosterDB Configuration cfg, ProcessKeyCache processKeyCache) {
        super(cfg);
        this.processKeyCache = processKeyCache;
    }

    public HostEntry get(UUID id) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOSTS.HOST_ID, NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME, NODE_ROSTER_HOSTS.CREATED_AT)
                .from(NODE_ROSTER_HOSTS)
                .where(NODE_ROSTER_HOSTS.HOST_ID.eq(id))
                .fetchOne(HostsDao::toHostEntry));
    }

    public List<HostEntry> list(HostFilter filter, Set<HostsDataInclude> includes, int limit, int offset) {
        NodeRosterHosts nrh = NODE_ROSTER_HOSTS.as("nrh");
        NodeRosterProcessHosts nrph = NODE_ROSTER_PROCESS_HOSTS.as("nrph");
        NodeRosterHostArtifacts nrha = NODE_ROSTER_HOST_ARTIFACTS.as("nrha");

        SelectQuery<Record> query = dsl().selectQuery();
        query.addSelect(nrh.HOST_ID, nrh.NORMALIZED_HOSTNAME, nrh.CREATED_AT);
        query.addFrom(nrh);

        if (filter.host() != null) {
            query.addConditions(nrh.NORMALIZED_HOSTNAME.containsIgnoreCase(filter.host()));
        }

        ProcessKey key = null;
        if (filter.processInstanceId() != null) {
            key = processKeyCache.get(filter.processInstanceId());
            if (key == null) {
                return Collections.emptyList();
            }

            query.addFrom(nrph);

            query.addConditions(nrh.HOST_ID.eq(nrph.HOST_ID)
                    .and(nrph.INSTANCE_ID.eq(key.getInstanceId())
                            .and(nrph.INSTANCE_CREATED_AT.eq(key.getCreatedAt()))));
        }

        if ((includes != null && includes.contains(HostsDataInclude.ARTIFACTS))
                || filter.artifact() != null) {
            query.addSelect(nrha.ARTIFACT_URL);
            query.addFrom(nrha);
            query.addConditions(nrh.HOST_ID.eq(nrha.HOST_ID));
        }

        if (filter.artifact() != null) {
            query.addConditions(nrha.ARTIFACT_URL.likeRegex(filter.artifact()));

            if (key != null) {
                query.addConditions(nrha.INSTANCE_ID.eq(key.getInstanceId())
                        .and(nrha.INSTANCE_CREATED_AT.eq(key.getCreatedAt())));
            }
        }

        query.addOrderBy(nrh.CREATED_AT.desc());
        query.addLimit(limit);
        query.addOffset(offset);

        return query.fetch(HostsDao::toHostEntry);
    }

    public List<ProcessEntry> listProcesses(UUID hostId, int limit, int offset) {
        NodeRosterProcessHosts h = NODE_ROSTER_PROCESS_HOSTS.as("h");

        Field<String> projectNameField = select(PROJECTS.PROJECT_NAME)
                .from(PROJECTS)
                .where(PROJECTS.PROJECT_ID.eq(h.PROJECT_ID)).asField();

        SelectJoinStep<Record6<UUID, OffsetDateTime, UUID, String, UUID, String>> q = dsl().select(
                h.INSTANCE_ID, h.INSTANCE_CREATED_AT,
                h.INITIATOR_ID, h.INITIATOR,
                h.PROJECT_ID, projectNameField)
                .from(h);

        return q.where(h.HOST_ID.eq(hostId))
                .orderBy(h.INSTANCE_CREATED_AT.desc())
                .limit(limit)
                .offset(offset)
                .fetch(HostsDao::toProcessEntry);
    }

    public UUID getId(String host) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOSTS.HOST_ID)
                .from(NODE_ROSTER_HOSTS)
                .where(NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME.eq(host))
                .fetchOne(NODE_ROSTER_HOSTS.HOST_ID));
    }

    public UUID insert(String host) {
        return txResult(tx -> tx.insertInto(NODE_ROSTER_HOSTS, NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .values(host)
                .returning(NODE_ROSTER_HOSTS.HOST_ID)
                .fetchOne()
                .getHostId());
    }

    public String getLastFacts(UUID hostId) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOST_FACTS.FACTS.cast(String.class))
                .from(NODE_ROSTER_HOST_FACTS)
                .where(NODE_ROSTER_HOST_FACTS.HOST_ID.eq(hostId))
                .orderBy(NODE_ROSTER_HOST_FACTS.SEQ_ID.desc())
                .limit(1)
                .fetchOne(Record1::value1));
    }

    private static <E> E getOrNull(Record r, Field<E> field) {
        Field<?> f = r.field(field);
        if (f == null) {
            return null;
        }

        return r.get(field);
    }

    private static HostEntry toHostEntry(Record r) {
        return HostEntry.builder()
                .id(r.getValue(NODE_ROSTER_HOSTS.HOST_ID))
                .name(r.getValue(NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME))
                .createdAt(r.getValue(NODE_ROSTER_HOSTS.CREATED_AT))
                .artifactUrl(getOrNull(r, NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL))
                .build();
    }

    private static ProcessEntry toProcessEntry(Record6<UUID, OffsetDateTime, UUID, String, UUID, String> r) {
        return ProcessEntry.builder()
                .instanceId(r.value1())
                .createdAt(r.value2())
                .initiatorId(r.value3())
                .initiator(r.value4())
                .projectId(r.value5())
                .projectName(r.value6())
                .build();
    }
}
