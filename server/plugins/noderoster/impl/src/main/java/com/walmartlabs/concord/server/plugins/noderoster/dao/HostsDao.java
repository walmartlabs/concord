package com.walmartlabs.concord.server.plugins.noderoster.dao;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.plugins.noderoster.HostEntry;
import com.walmartlabs.concord.server.plugins.noderoster.InitiatorEntry;
import com.walmartlabs.concord.server.plugins.noderoster.db.NodeRosterDB;
import org.jooq.Configuration;
import org.jooq.Record1;
import org.jooq.Record2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostArtifacts.NODE_ROSTER_HOST_ARTIFACTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHostFacts.NODE_ROSTER_HOST_FACTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHosts.NODE_ROSTER_HOSTS;
import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterProcessHosts.NODE_ROSTER_PROCESS_HOSTS;

public class HostsDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(HostsDao.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public HostsDao(@NodeRosterDB Configuration cfg) {
        super(cfg);
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

    public InitiatorEntry getLastInitiator(UUID hostId) {
        return txResult(tx -> tx.select(NODE_ROSTER_PROCESS_HOSTS.INITIATOR_ID, NODE_ROSTER_PROCESS_HOSTS.INITIATOR)
                .from(NODE_ROSTER_PROCESS_HOSTS)
                .where(NODE_ROSTER_PROCESS_HOSTS.HOST_ID.eq(hostId))
                .orderBy(NODE_ROSTER_PROCESS_HOSTS.INSTANCE_CREATED_AT.desc())
                .limit(1)
                .fetchOne(r -> InitiatorEntry.builder()
                        .userId(r.get(NODE_ROSTER_PROCESS_HOSTS.INITIATOR_ID))
                        .username(r.get(NODE_ROSTER_PROCESS_HOSTS.INITIATOR))
                        .build()));
    }

    public Object getFacts(UUID hostId) {
        try {
            String s = getFactsString(hostId);
            return objectMapper.readValue(s, Object.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getFactsString(UUID hostId) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOST_FACTS.FACTS.cast(String.class))
                .from(NODE_ROSTER_HOST_FACTS)
                .innerJoin(NODE_ROSTER_PROCESS_HOSTS).on(NODE_ROSTER_PROCESS_HOSTS.HOST_ID.eq(NODE_ROSTER_HOST_FACTS.HOST_ID))
                .where(NODE_ROSTER_PROCESS_HOSTS.HOST_ID.eq(hostId))
                .orderBy(NODE_ROSTER_HOST_FACTS.INSTANCE_CREATED_AT.desc())
                .limit(1)
                .fetchOne(Record1::value1));

    }

    public List<HostEntry> findTouchedHosts(UUID projectId, int limit, int offset) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOSTS.HOST_ID, NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .from(NODE_ROSTER_HOSTS)
                .innerJoin(NODE_ROSTER_PROCESS_HOSTS).on(NODE_ROSTER_PROCESS_HOSTS.HOST_ID.eq(NODE_ROSTER_HOSTS.HOST_ID))
                .where(NODE_ROSTER_PROCESS_HOSTS.PROJECT_ID.eq(projectId))
                .orderBy(NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .limit(limit)
                .offset(offset)
                .fetch(HostsDao::toHostEntry));
    }

    public List<String> getMatchingArtifacts(String artifactPattern, int limit, int offset) {
        return txResult(tx -> tx.selectDistinct(NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL)
                .from(NODE_ROSTER_HOST_ARTIFACTS)
                .where(NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL.likeRegex(artifactPattern))
                .orderBy(NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL)
                .limit(limit)
                .offset(offset)
                .fetch(NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL));
    }

    public List<HostEntry> getAllKnownHosts(int limit, int offset) {
        return txResult(tx -> tx.select(NODE_ROSTER_HOSTS.HOST_ID, NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .from(NODE_ROSTER_HOSTS)
                .orderBy(NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .limit(limit)
                .offset(offset)
                .fetch(HostsDao::toHostEntry));
    }

    public List<HostEntry> getHosts(String artifactUrl) {
        return txResult(tx -> tx.selectDistinct(NODE_ROSTER_HOSTS.HOST_ID, NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME)
                .from(NODE_ROSTER_HOSTS)
                .innerJoin(NODE_ROSTER_HOST_ARTIFACTS).on(NODE_ROSTER_HOST_ARTIFACTS.HOST_ID.eq(NODE_ROSTER_HOSTS.HOST_ID))
                .where(NODE_ROSTER_HOST_ARTIFACTS.ARTIFACT_URL.eq(artifactUrl))
                .fetch(HostsDao::toHostEntry));
    }

    private static HostEntry toHostEntry(Record2<UUID, String> r) {
        return HostEntry.builder()
                .hostId(r.value1())
                .hostName(r.value2())
                .build();
    }
}
