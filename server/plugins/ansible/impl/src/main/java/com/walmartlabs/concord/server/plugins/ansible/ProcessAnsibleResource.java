package com.walmartlabs.concord.server.plugins.ansible;

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
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleHosts;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.records.AnsibleHostsRecord;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.immutables.value.Value;
import org.jooq.Configuration;
import org.jooq.Record8;
import org.jooq.SelectConditionStep;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

import static com.walmartlabs.concord.server.plugins.ansible.ProcessAnsibleResource.AnsibleHostStatus.*;
import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.ANSIBLE_HOSTS;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
@Api(value = "Ansible Process", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public class ProcessAnsibleResource implements Resource {

    private final ProcessKeyCache processKeyCache;
    private final EventDao eventDao;
    private final AnsibleHostsDao ansibleHostsDao;

    @Inject
    public ProcessAnsibleResource(ProcessKeyCache processKeyCache, EventDao eventDao, AnsibleHostsDao ansibleHostsDao) {
        this.processKeyCache = processKeyCache;
        this.eventDao = eventDao;
        this.ansibleHostsDao = ansibleHostsDao;
    }

    /**
     * Lists Ansible hosts of a specific process.
     */
    @GET
    @ApiOperation("List Ansible hosts of a specific process")
    @Path("/{processInstanceId}/ansible/hosts")
    @Produces(MediaType.APPLICATION_JSON)
//    @WithTimer
    public List<AnsibleHostEntry> list(@PathParam("processInstanceId") UUID processInstanceId,
                                       @QueryParam("host") String host,
                                       @QueryParam("hostGroup") String hostGroup,
                                       @QueryParam("status") AnsibleHostStatus status,
                                       @QueryParam("limit") @DefaultValue("30") int limit,
                                       @QueryParam("offset") @DefaultValue("0") int offset) {

        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        return ansibleHostsDao.list(key.getInstanceId(), key.getCreatedAt(), host, hostGroup, status, limit, offset);
    }

    /**
     * Returns Ansible statistics of a specific process.
     */
    @GET
    @ApiOperation("Returns Ansible statistics of a specific process")
    @Path("/{processInstanceId}/ansible/stats")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public AnsibleStatsEntry stats(@PathParam("processInstanceId") UUID processInstanceId) {
        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            throw new ConcordApplicationException("Process instance not found", Response.Status.NOT_FOUND);
        }

        return ansibleHostsDao.getStats(key.getInstanceId(), key.getCreatedAt());
    }

    /**
     * Lists Ansible events of a specific process.
     */
    @GET
    @ApiOperation("List Ansible events of a specific process")
    @Path("/{processInstanceId}/ansible/events")
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

        return eventDao.list(key, eventFilter);
    }

    @Named
    public static class AnsibleHostsDao extends AbstractDao {

        @Inject
        protected AnsibleHostsDao(Configuration cfg) {
            super(cfg);
        }

        public AnsibleStatsEntry getStats(UUID instanceId, Timestamp instanceCreatedAt) {
            return txResult(tx -> {
                AnsibleHosts a = ANSIBLE_HOSTS.as("a");

                Record8<Integer, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal, String[]> r = tx.select(
                        countDistinct(a.HOST),
                        sum(when(a.STATUS.eq(RUNNING.name()), 1).otherwise(0)).as(RUNNING.name()),
                        sum(when(a.STATUS.eq(CHANGED.name()), 1).otherwise(0)).as(CHANGED.name()),
                        sum(when(a.STATUS.eq(FAILED.name()), 1).otherwise(0)).as(FAILED.name()),
                        sum(when(a.STATUS.eq(OK.name()), 1).otherwise(0)).as(OK.name()),
                        sum(when(a.STATUS.eq(SKIPPED.name()), 1).otherwise(0)).as(SKIPPED.name()),
                        sum(when(a.STATUS.eq(UNREACHABLE.name()), 1).otherwise(0)).as(UNREACHABLE.name()),
                        arrayAggDistinct(a.HOST_GROUP))
                        .from(a)
                        .where(a.INSTANCE_ID.eq(instanceId).and(a.INSTANCE_CREATED_AT.eq(instanceCreatedAt)))
                        .fetchOne();

                if (r == null || r.value1() == 0) {
                    return ImmutableAnsibleStatsEntry.builder()
                            .uniqueHosts(0)
                            .addAllHostGroups(Collections.emptyList())
                            .putAllStats(Collections.emptyMap())
                            .build();
                }

                return ImmutableAnsibleStatsEntry.builder()
                        .uniqueHosts(r.value1())
                        .putStats(RUNNING, r.value2().intValue())
                        .putStats(CHANGED, r.value3().intValue())
                        .putStats(FAILED, r.value4().intValue())
                        .putStats(OK, r.value5().intValue())
                        .putStats(SKIPPED, r.value6().intValue())
                        .putStats(UNREACHABLE, r.value7().intValue())
                        .addHostGroups(r.value8())
                        .build();
            });
        }

        public List<AnsibleHostEntry> list(UUID instanceId, Timestamp instanceCreatedAt,
                                           String host, String hostGroup,
                                           AnsibleHostStatus status,
                                           int limit, int offset) {
            return txResult(tx -> {
                SelectConditionStep<AnsibleHostsRecord> q = tx.selectFrom(ANSIBLE_HOSTS)
                        .where(ANSIBLE_HOSTS.INSTANCE_ID.eq(instanceId)
                                .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(instanceCreatedAt)));

                if (host != null) {
                    q.and(ANSIBLE_HOSTS.HOST.contains(host));
                }

                if (hostGroup != null) {
                    q.and(ANSIBLE_HOSTS.HOST_GROUP.eq(hostGroup));
                }

                if (status != null) {
                    q.and(ANSIBLE_HOSTS.STATUS.eq(status.name()));
                }

                return q.orderBy(ANSIBLE_HOSTS.HOST)
                        .limit(limit)
                        .offset(offset)
                        .fetch(AnsibleHostsDao::toEntity);
            });
        }

        private static AnsibleHostEntry toEntity(AnsibleHostsRecord r) {
            return ImmutableAnsibleHostEntry.builder()
                    .host(r.getHost())
                    .hostGroup(r.getHostGroup())
                    .status(AnsibleHostStatus.valueOf(r.getStatus()))
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

        AnsibleHostStatus status();

        long duration();
    }

    @Value.Immutable
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutableAnsibleStatsEntry.class)
    @JsonDeserialize(as = ImmutableAnsibleStatsEntry.class)
    public interface AnsibleStatsEntry {

        int uniqueHosts();

        Map<AnsibleHostStatus, Integer> stats();

        Set<String> hostGroups();
    }

    public enum AnsibleHostStatus {
        RUNNING,
        CHANGED,
        FAILED,
        OK,
        SKIPPED,
        UNREACHABLE
    }
}
