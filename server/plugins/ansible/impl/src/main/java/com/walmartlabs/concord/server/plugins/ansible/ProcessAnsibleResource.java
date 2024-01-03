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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleHosts;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsiblePlayStats;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsiblePlaybookStats;
import com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleTaskStats;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.immutables.value.Value;
import org.jooq.*;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.plugins.ansible.ProcessAnsibleResource.AnsibleHostStatus.FAILED;
import static com.walmartlabs.concord.server.plugins.ansible.ProcessAnsibleResource.AnsibleHostStatus.UNREACHABLE;
import static com.walmartlabs.concord.server.plugins.ansible.jooq.Tables.*;
import static com.walmartlabs.concord.server.plugins.ansible.jooq.tables.AnsibleTaskStats.ANSIBLE_TASK_STATS;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
@Path("/api/v1/process")
@Tag(name = "Ansible Process")
public class ProcessAnsibleResource implements Resource {

    private final ProcessKeyCache processKeyCache;
    private final EventDao eventDao;
    private final AnsibleDao ansibleDao;

    @Inject
    public ProcessAnsibleResource(ProcessKeyCache processKeyCache, EventDao eventDao, AnsibleDao ansibleDao) {
        this.processKeyCache = processKeyCache;
        this.eventDao = eventDao;
        this.ansibleDao = ansibleDao;
    }

    @GET
    @Operation(description = "List Ansible playbooks of a specific process", operationId = "listPlaybooks")
    @Path("/{processInstanceId}/ansible/playbooks")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PlaybookEntry> listPlaybooks(@PathParam("processInstanceId") UUID processInstanceId) {
        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        return ansibleDao.listPlaybooks(key.getInstanceId(), key.getCreatedAt());
    }

    @GET
    @Operation(description = "List Ansible plays of a specific process", operationId = "listPlays")
    @Path("/{processInstanceId}/ansible/{playbookId}/plays")
    @Produces(MediaType.APPLICATION_JSON)
    public List<PlayInfo> listPlays(@PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("playbookId") UUID playbookId) {
        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        return ansibleDao.listPlays(key.getInstanceId(), key.getCreatedAt(), playbookId);
    }

    @GET
    @Operation(description = "List Ansible plays of a specific process", operationId = "listTasks")
    @Path("/{processInstanceId}/ansible/tasks")
    @Produces(MediaType.APPLICATION_JSON)
    public List<TaskInfo> listTasks(@PathParam("processInstanceId") UUID processInstanceId,
                                    @QueryParam("playId") UUID playId) {
        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        return ansibleDao.listTasks(key.getInstanceId(), key.getCreatedAt(), playId);
    }


    /**
     * Lists Ansible hosts of a specific process.
     */
    @GET
    @Operation(description = "List Ansible hosts of a specific process", operationId = "listAnsibleHosts")
    @Path("/{processInstanceId}/ansible/hosts")
    @Produces(MediaType.APPLICATION_JSON)
    public AnsibleHostListResponse list(@PathParam("processInstanceId") UUID processInstanceId,
                                        @QueryParam("host") String host,
                                        @QueryParam("hostGroup") String hostGroup,
                                        @QueryParam("status") AnsibleHostStatus status,
                                        @QueryParam("statuses") List<AnsibleHostStatus> statuses,
                                        @QueryParam("playbookId") UUID playbookId,
                                        @QueryParam("limit") @DefaultValue("30") int limit,
                                        @QueryParam("offset") @DefaultValue("0") int offset,
                                        @QueryParam("sortField") SortField sortField,
                                        @QueryParam("sortBy") SortBy sortBy) {

        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return null;
        }

        if (status != null) {
            if (statuses == null) {
                statuses = new ArrayList<>();
            }
            statuses.add(status);
        }

        List<AnsibleHostEntry> hosts = ansibleDao.list(key.getInstanceId(), key.getCreatedAt(), host, hostGroup, statuses, playbookId, limit, offset, sortField, sortBy);
        List<String> hostGroups = ansibleDao.listHostGroups(key.getInstanceId(), key.getCreatedAt(), playbookId);
        return ImmutableAnsibleHostListResponse.builder()
                .items(hosts)
                .hostGroups(hostGroups)
                .build();
    }

    /**
     * Lists Ansible events of a specific process.
     */
    @GET
    @Operation(description = "List Ansible events of a specific process", operationId = "listEvents")
    @Path("/{processInstanceId}/ansible/events")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessEventEntry> listEvents(@PathParam("processInstanceId") UUID processInstanceId,
                                              @QueryParam("host") String host,
                                              @QueryParam("hostGroup") String hostGroup,
                                              @QueryParam("status") String status,
                                              @QueryParam("playbookId") UUID playbookId) {

        ProcessKey key = processKeyCache.get(processInstanceId);
        if (key == null) {
            return Collections.emptyList();
        }

        Map<String, String> eventFilter = new HashMap<>();
        if (host != null) {
            eventFilter.put("host", host);
        }
        if (hostGroup != null) {
            eventFilter.put("hostGroup", hostGroup);
        }
        if (status != null) {
            eventFilter.put("status", status);
        }
        if (playbookId != null) {
            eventFilter.put("playbookId", playbookId.toString());
        }

        return eventDao.list(key, eventFilter);
    }

    @Named
    public static class AnsibleDao extends AbstractDao {

        @Inject
        protected AnsibleDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public List<PlayInfo> listPlays(UUID instanceId, OffsetDateTime instanceCreatedAt, UUID playbookId) {
            return txResult(tx -> {
                AnsiblePlayStats a = ANSIBLE_PLAY_STATS.as("a");
                AnsibleTaskStats s = ANSIBLE_TASK_STATS.as("s");

                return tx.select(
                        a.PLAY_ID,
                        a.PLAY_NAME,
                        a.PLAY_ORDER,
                        a.HOST_COUNT,
                        a.TASK_COUNT,
                        a.FINISHED_TASK_COUNT,
                        sum(s.OK_COUNT).as("ok"),
                        sum(s.FAILED_COUNT).as("failed"),
                        sum(s.UNREACHABLE_COUNT).as("unreachable"),
                        sum(s.SKIPPED_COUNT).as("skipped"),
                        sum(s.RUNNING_COUNT).as("running"))
                        .from(a)
                        .leftJoin(s).on(s.INSTANCE_ID.eq(instanceId)
                                .and(s.INSTANCE_CREATED_AT.eq(instanceCreatedAt)
                                        .and(s.PLAY_ID.eq(a.PLAY_ID))))
                        .where(a.INSTANCE_ID.eq(instanceId)
                                .and(a.INSTANCE_CREATED_AT.eq(instanceCreatedAt)
                                        .and(a.PLAYBOOK_ID.eq(playbookId))))
                        .groupBy(a.PLAY_ID, a.PLAY_NAME, a.PLAY_ORDER, a.HOST_COUNT, a.TASK_COUNT, a.FINISHED_TASK_COUNT)
                        .fetch(r -> ImmutablePlayInfo.builder()
                                .playId(r.get(a.PLAY_ID))
                                .playName(r.get(a.PLAY_NAME) != null ? r.get(a.PLAY_NAME) : "n/a")
                                .playOrder(r.get(a.PLAY_ORDER))
                                .hostCount(r.get(a.HOST_COUNT))
                                .taskCount(r.get(a.TASK_COUNT))
                                .putAllTaskStats(getTaskStats(r))
                                .finishedTaskCount(r.get(a.FINISHED_TASK_COUNT))
                                .build());
            });
        }

        private Map<String, Long> getTaskStats(Record11<UUID, String, Integer, Long, Integer, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal> r) {
            Map<String, Long> result = new HashMap<>();
            result.put("ok", getTaskCount(r, "ok"));
            result.put("failed", getTaskCount(r, "failed"));
            result.put("unreachable", getTaskCount(r, "unreachable"));
            result.put("skipped", getTaskCount(r, "skipped"));
            result.put("running", getTaskCount(r, "running"));
            return result;
        }

        private static Long getTaskCount(Record11<UUID, String, Integer, Long, Integer, Long, BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal> r, String key) {
            Object v = r.get(key);
            if (v == null) {
                return 0L;
            }

            long result = ((BigDecimal)v).longValue();
            if (result < 0) {
                return 0L;
            }

            return result;
        }

        public List<TaskInfo> listTasks(UUID instanceId, OffsetDateTime instanceCreatedAt, UUID playId) {
            return txResult(tx -> {
                AnsibleTaskStats a = ANSIBLE_TASK_STATS.as("a");
                return tx.select(a.TASK_NAME, a.TASK_ORDER, a.TASK_TYPE, a.OK_COUNT, a.FAILED_COUNT, a.UNREACHABLE_COUNT, a.SKIPPED_COUNT, a.RUNNING_COUNT)
                        .from(a)
                        .where(a.INSTANCE_ID.eq(instanceId)
                                .and(a.INSTANCE_CREATED_AT.eq(instanceCreatedAt)
                                        .and(a.PLAY_ID.eq(playId))))
                        .fetch(r -> ImmutableTaskInfo.builder()
                                .taskName(r.get(a.TASK_NAME))
                                .taskOrder(r.get(a.TASK_ORDER))
                                .type(TaskType.valueOf(r.get(a.TASK_TYPE)))
                                .okCount(r.get(a.OK_COUNT))
                                .failedCount(r.get(a.FAILED_COUNT))
                                .unreachableCount(r.get(a.UNREACHABLE_COUNT))
                                .skippedCount(r.get(a.SKIPPED_COUNT))
                                .runningCount(r.get(a.RUNNING_COUNT) < 0 ? 0 : r.get(a.RUNNING_COUNT))
                                .build());
            });
        }

        public List<String> listHostGroups(UUID instanceId, OffsetDateTime instanceCreatedAt, UUID playbookId) {
            return txResult(tx -> {
                AnsibleHosts a = ANSIBLE_HOSTS.as("a");
                SelectConditionStep<Record1<String>> q = tx.selectDistinct(a.HOST_GROUP)
                        .from(a)
                        .where(a.INSTANCE_ID.eq(instanceId)
                                .and(a.INSTANCE_CREATED_AT.eq(instanceCreatedAt)));

                if (playbookId != null) {
                    q = q.and(a.PLAYBOOK_ID.eq(playbookId));
                }

                return q.fetch(Record1::value1);
            });
        }

        public List<AnsibleHostEntry> list(UUID instanceId, OffsetDateTime instanceCreatedAt,
                                           String host, String hostGroup,
                                           List<AnsibleHostStatus> statuses,
                                           UUID playbookId,
                                           int limit, int offset, SortField sortField, SortBy sortBy) {
            
            Field<String> orderField = assertSortField(sortField);
            
            return txResult(tx -> {
                AnsibleHosts a = ANSIBLE_HOSTS.as("a");
                SelectConditionStep<Record4<String, String, Long, String>> q =
                        tx.select(
                                a.HOST,
                                a.HOST_GROUP,
                                a.DURATION,
                                a.STATUS)
                                .from(a)
                                .where(a.INSTANCE_ID.eq(instanceId)
                                        .and(a.INSTANCE_CREATED_AT.eq(instanceCreatedAt)));

                if (host != null) {
                    q.and(a.HOST.contains(host));
                }

                if (hostGroup != null) {
                    q.and(a.HOST_GROUP.eq(hostGroup));
                }

                if (statuses != null && !statuses.isEmpty()) {
                    q.and(a.STATUS.in(statuses.stream().map(Enum::name).collect(Collectors.toList())));
                }

                if (playbookId != null) {
                    q.and(a.PLAYBOOK_ID.eq(playbookId));
                }
                
                if (orderField != null) {
                    if (sortBy != null && sortBy.equals(SortBy.DESC)) {
                        q.orderBy(orderField.desc());
                    }
                    q.orderBy(orderField.asc());
                }
                else {
                    q.orderBy(a.HOST);
                }
                
                return q.limit(limit)
                        .offset(offset)
                        .fetch(AnsibleDao::toHostEntity);
            });
        }

        private static Field<String> assertSortField(SortField sortField) { 
            if (sortField == null) {
                return null;
            }
            
            Field<String> orderField = (Field<String>) ANSIBLE_HOSTS.as("a").field(sortField.name().toLowerCase());
            if (orderField == null) {
                throw new ValidationErrorsException("Invalid sort field: " + sortField.name());
            }
            
            return orderField;
        }

        public List<PlaybookEntry> listPlaybooks(UUID instanceId, OffsetDateTime createdAt) {
            return txResult(tx -> {
                AnsiblePlaybookStats p = ANSIBLE_PLAYBOOK_STATS.as("p");

                Field<Integer> failedHosts = tx.select(count()).from(ANSIBLE_HOSTS).where(ANSIBLE_HOSTS.STATUS.in(FAILED.name(), UNREACHABLE.name())
                        .and(ANSIBLE_HOSTS.INSTANCE_ID.eq(instanceId)
                                .and(ANSIBLE_HOSTS.INSTANCE_CREATED_AT.eq(createdAt)
                                        .and(ANSIBLE_HOSTS.PLAYBOOK_ID.eq(p.PLAYBOOK_ID)))))
                        .asField("failedHostsCount");

                AnsiblePlayStats s = ANSIBLE_PLAY_STATS;
                Field<Long> totalPlayWork = s.HOST_COUNT.mul(s.TASK_COUNT);
                Field<BigDecimal> finishedTasks = tx.select(sum(
                        when(s.FINISHED_TASK_COUNT.greaterThan(totalPlayWork), totalPlayWork)
                                .otherwise(s.FINISHED_TASK_COUNT)))
                        .from(s)
                        .where(s.INSTANCE_ID.eq(instanceId)
                                .and(s.INSTANCE_CREATED_AT.eq(createdAt)
                                        .and(s.PLAYBOOK_ID.eq(p.PLAYBOOK_ID))))
                        .asField("finishedTasksCount");

                Field<BigDecimal> failedTasks = tx.select(sum(ANSIBLE_TASK_STATS.FAILED_COUNT))
                        .from(ANSIBLE_TASK_STATS)
                        .where(ANSIBLE_TASK_STATS.INSTANCE_ID.eq(instanceId)
                                .and(ANSIBLE_TASK_STATS.INSTANCE_CREATED_AT.eq(createdAt)
                                        .and(ANSIBLE_TASK_STATS.PLAYBOOK_ID.eq(p.PLAYBOOK_ID))))
                        .asField("failedTasksCount");

                Field<String> playbookStatus = tx.select(ANSIBLE_PLAYBOOK_RESULT.STATUS)
                        .from(ANSIBLE_PLAYBOOK_RESULT)
                        .where(ANSIBLE_PLAYBOOK_RESULT.INSTANCE_ID.eq(instanceId)
                                .and(ANSIBLE_PLAYBOOK_RESULT.INSTANCE_CREATED_AT.eq(createdAt)
                                        .and(ANSIBLE_PLAYBOOK_RESULT.PLAYBOOK_ID.eq(p.PLAYBOOK_ID))))
                        .asField("playbookResult");

                return tx.select(p.PLAYBOOK_ID,
                        p.NAME,
                        p.STARTED_AT,
                        p.HOST_COUNT,
                        failedHosts,
                        p.PLAY_COUNT,
                        failedTasks,
                        finishedTasks,
                        playbookStatus,
                        p.TOTAL_WORK,
                        p.RETRY_NUM)
                        .from(p)
                        .where(p.INSTANCE_CREATED_AT.eq(createdAt)
                                .and(p.INSTANCE_ID.eq(instanceId)))
                        .fetch(AnsibleDao::toPlaybookEntry);
            });
        }

        private static PlaybookEntry toPlaybookEntry(Record11<UUID, String, OffsetDateTime, Integer, Integer, Integer, BigDecimal, BigDecimal, String, Integer, Integer> r) {
            long totalWork = r.value10().longValue();
            long finishedCount = r.value8().longValue();
            PlaybookStatus status = r.value9() == null ? PlaybookStatus.RUNNING : PlaybookStatus.valueOf(r.value9());
            int progress;
            if (status == PlaybookStatus.OK || status == PlaybookStatus.FAILED || totalWork == 0) {
                progress = 100;
            } else {
                progress = (int) (100 * finishedCount / totalWork);
            }

            return ImmutablePlaybookEntry.builder()
                    .id(r.value1())
                    .name(r.value2())
                    .startedAt(r.value3())
                    .hostsCount(r.value4())
                    .failedHostsCount(r.value5() == null ? 0 : r.value5())
                    .playsCount(r.value6())
                    .failedTasksCount(r.value7() == null ? 0 : r.value7().longValue())
                    .progress(progress)
                    .status(status)
                    .retryNum(r.value11())
                    .build();
        }

        private static AnsibleHostEntry toHostEntity(Record4<String, String, Long, String> r) {
            return ImmutableAnsibleHostEntry.builder()
                    .host(r.value1())
                    .hostGroup(r.value2())
                    .duration(r.value3())
                    .status(AnsibleHostStatus.valueOf(r.value4()))
                    .build();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
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
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutableAnsibleHostListResponse.class)
    @JsonDeserialize(as = ImmutableAnsibleHostListResponse.class)
    public interface AnsibleHostListResponse {

        List<String> hostGroups();

        List<AnsibleHostEntry> items();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutablePlaybookEntry.class)
    @JsonDeserialize(as = ImmutablePlaybookEntry.class)
    public interface PlaybookEntry {

        UUID id();

        String name();

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
        OffsetDateTime startedAt();

        long hostsCount();

        long failedHostsCount();

        int playsCount();

        long failedTasksCount();

        int progress();

        PlaybookStatus status();

        @Nullable
        Integer retryNum();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutablePlayInfo.class)
    @JsonDeserialize(as = ImmutablePlayInfo.class)
    public interface PlayInfo {

        UUID playId();

        String playName();

        int playOrder();

        long hostCount();

        int taskCount();

        Map<String, Long> taskStats();

        long finishedTaskCount();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(as = ImmutableTaskInfo.class)
    @JsonDeserialize(as = ImmutableTaskInfo.class)
    public interface TaskInfo {

        String taskName();

        TaskType type();

        long taskOrder();

        long okCount();

        long failedCount();

        long unreachableCount();

        long skippedCount();

        long runningCount();
    }

    public enum AnsibleHostStatus {
        RUNNING,
        CHANGED,
        FAILED,
        OK,
        SKIPPED,
        UNREACHABLE
    }

    public enum PlaybookStatus {
        RUNNING,
        OK,
        FAILED
    }

    public enum TaskType {
        TASK,
        SETUP,
        HANDLER
    }
    
    public enum SortField {
        HOST,
        DURATION,
        STATUS,
        HOST_GROUP
    }
    
    public enum SortBy {
        ASC,
        DESC
    }
}
