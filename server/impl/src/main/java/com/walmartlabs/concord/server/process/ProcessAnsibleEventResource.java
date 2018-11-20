package com.walmartlabs.concord.server.process;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.sdk.EventType;
import com.walmartlabs.concord.server.jooq.tables.records.AnsibleHostsRecord;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.event.EventDao;
import com.walmartlabs.concord.server.process.event.ProcessEventEntry;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.sql.Timestamp;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.Tables.ANSIBLE_HOSTS;

@Named
@Singleton
@Path("/api/v1/process")
public class ProcessAnsibleEventResource implements Resource {

    private final ProcessKeyCache processKeyCache;
    private final EventDao eventDao;
    private final AnsibleHostsDao ansibleHostsDao;

    @Inject
    public ProcessAnsibleEventResource(ProcessKeyCache processKeyCache, EventDao eventDao, AnsibleHostsDao ansibleHostsDao) {
        this.processKeyCache = processKeyCache;
        this.eventDao = eventDao;
        this.ansibleHostsDao = ansibleHostsDao;
    }

    /**
     * List process ansible hosts.
     *
     * @param processInstanceId
     * @return
     */
    @GET
    @Path("/{processInstanceId}/ansibleHosts")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<AnsibleHostEntry> list(@PathParam("processInstanceId") UUID processInstanceId) {
        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        return ansibleHostsDao.list(key.getInstanceId(), key.getCreatedAt());
    }

    @GET
    @Path("/{processInstanceId}/ansibleEvents")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEventEntry> listEvents(@PathParam("processInstanceId") UUID processInstanceId,
                                              @QueryParam("host") String host,
                                              @QueryParam("hostGroup") String hostGroup,
                                              @QueryParam("status") String status) {

        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        Map<String, Object> eventFilter = new HashMap<>();
        if (host != null) {
            eventFilter.put("host", host);
        }
        if (hostGroup != null) {
            eventFilter.put("hostGroup", hostGroup);
        }
        if (status != null) {
            eventFilter.put("status", status);
        }

        return eventDao.list(key, null, EventType.ANSIBLE.name(), eventFilter, -1);
    }

    @Named
    public static class AnsibleHostsDao extends AbstractDao {

        @Inject
        protected AnsibleHostsDao(Configuration cfg) {
            super(cfg);
        }

        public List<AnsibleHostEntry> list(UUID instanceId, Timestamp instanceCreatedAt) {
            return txResult(tx -> tx.selectFrom(ANSIBLE_HOSTS)
                    .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(instanceId)
                            .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                    .fetch(AnsibleHostsDao::toEntity));
        }

        private static AnsibleHostEntry toEntity(AnsibleHostsRecord r) {
            return ImmutableAnsibleHostEntry.builder()
                    .host(r.getHost())
                    .hostGroup(r.getHostGroup())
                    .status(r.getStatus())
                    .duration(r.getDuration())
                    .build();
        }
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutableAnsibleHostEntry.class)
    @JsonDeserialize(as = ImmutableAnsibleHostEntry.class)
    public interface AnsibleHostEntry {

        String host();

        String hostGroup();

        String status();

        long duration();
    }
}
