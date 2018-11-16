package com.walmartlabs.concord.server.console;

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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.console.UserActivityResponse.ProjectProcesses;
import static com.walmartlabs.concord.server.jooq.Tables.V_PROCESS_QUEUE;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.process.ProcessStatus.*;
import static org.jooq.impl.DSL.*;

@Named
@Singleton
@Path("/api/service/console/user")
public class UserActivityResource implements Resource {

    private final Set<ProcessStatus> ORG_VISIBLE_STATUSES = new HashSet<>(Collections.singletonList(ProcessStatus.RUNNING));

    private final UserDao userDao;
    private final ProcessQueueDao processDao;
    private final ProcessStatsDao processStatsDao;

    @Inject
    public UserActivityResource(UserDao userDao, ProcessQueueDao processDao, ProcessStatsDao processStatsDao) {
        this.userDao = userDao;
        this.processDao = processDao;
        this.processStatsDao = processStatsDao;
    }

    @GET
    @Path("/activity")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public UserActivityResponse activity(@QueryParam("maxProjectsPerOrg") @DefaultValue("5") int maxProjectsPerOrg,
                                         @QueryParam("maxOwnProcesses") @DefaultValue("5") int maxOwnProcesses) {

        UserPrincipal user = UserPrincipal.assertCurrent();
        Set<UUID> orgIds = userDao.getOrgIds(user.getId());
        Timestamp t = new Timestamp(startOfDay(new Date()).getTime());

        Map<String, List<ProjectProcesses>> orgProcesses = processStatsDao.processByOrgs(maxProjectsPerOrg, orgIds, ORG_VISIBLE_STATUSES, t);

        Map<String, Integer> stats = processStatsDao.getCountByStatuses(orgIds, t, user.getId());

        ProcessFilter filter = ProcessFilter.builder()
                .includeWithoutProjects(true)
                .initiator(user.getUsername())
                .ordIds(orgIds)
                .build();
        List<ProcessEntry> lastProcesses = processDao.list(filter, maxOwnProcesses, 0);

        return new UserActivityResponse(stats, orgProcesses, lastProcesses);
    }

    private static Date startOfDay(Date date) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        LocalDateTime startOfDay = localDateTime.with(LocalTime.MIN);
        return Date.from(startOfDay.atZone(ZoneId.systemDefault()).toInstant());
    }

    @Named
    private static class ProcessStatsDao extends AbstractDao {

        @Inject
        protected ProcessStatsDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public Map<String, Integer> getCountByStatuses(Set<UUID> orgIds, Timestamp fromUpdatedAt, UUID initiatorId) {
            try (DSLContext tx = DSL.using(cfg)) {
                SelectConditionStep<Record1<UUID>> projectIds = select(PROJECTS.PROJECT_ID)
                        .from(PROJECTS)
                        .where(PROJECTS.ORG_ID.in(orgIds));

                SelectConditionStep<Record5<Integer, Integer, Integer, Integer, Integer>> q = tx.select(
                        when(V_PROCESS_QUEUE.CURRENT_STATUS.eq(RUNNING.name()), 1).otherwise(0).as(RUNNING.name()),
                        when(V_PROCESS_QUEUE.CURRENT_STATUS.eq(SUSPENDED.name()), 1).otherwise(0).as(SUSPENDED.name()),
                        when(V_PROCESS_QUEUE.CURRENT_STATUS.eq(FINISHED.name()), 1).otherwise(0).as(FINISHED.name()),
                        when(V_PROCESS_QUEUE.CURRENT_STATUS.eq(FAILED.name()), 1).otherwise(0).as(FAILED.name()),
                        when(V_PROCESS_QUEUE.CURRENT_STATUS.eq(ENQUEUED.name()), 1).otherwise(0).as(ENQUEUED.name()))
                        .from(V_PROCESS_QUEUE)
                        .where(V_PROCESS_QUEUE.INITIATOR_ID.eq(initiatorId)
                                .and(V_PROCESS_QUEUE.LAST_UPDATED_AT.greaterOrEqual(fromUpdatedAt))
                                .and(or(V_PROCESS_QUEUE.PROJECT_ID.in(projectIds), V_PROCESS_QUEUE.PROJECT_ID.isNull())));

                Record5<BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal> r = tx.select(
                        sum(q.field(RUNNING.name(), Integer.class)),
                        sum(q.field(SUSPENDED.name(), Integer.class)),
                        sum(q.field(FINISHED.name(), Integer.class)),
                        sum(q.field(FAILED.name(), Integer.class)),
                        sum(q.field(ENQUEUED.name(), Integer.class)))
                        .from(q)
                        .fetchOne();

                Map<String, Integer> result = new HashMap<>();
                result.put(RUNNING.name(), r.value1() != null ? r.value1().intValue() : 0);
                result.put(SUSPENDED.name(), r.value2() != null ? r.value2().intValue() : 0);
                result.put(FINISHED.name(), r.value3() != null ? r.value3().intValue() : 0);
                result.put(FAILED.name(), r.value4() != null ? r.value4().intValue() : 0);
                result.put(ENQUEUED.name(), r.value5() != null ? r.value5().intValue() : 0);
                return result;
            }
        }

        public Map<String, List<ProjectProcesses>> processByOrgs(int maxProjectRows,
                                                                 Set<UUID> orgIds,
                                                                 Set<ProcessStatus> processStatuses,
                                                                 Timestamp fromUpdatedAt) {

            Set<String> statuses = processStatuses.stream().map(Enum::name).collect(Collectors.toSet());

            try (DSLContext tx = DSL.using(cfg)) {
                WindowRowsStep<Integer> rnField = rowNumber().over().partitionBy(V_PROCESS_QUEUE.ORG_NAME).orderBy(V_PROCESS_QUEUE.ORG_NAME);

                SelectHavingStep<Record4<String, String, Integer, Integer>> a =
                        tx.select(V_PROCESS_QUEUE.ORG_NAME, V_PROCESS_QUEUE.PROJECT_NAME, count(), rnField)
                            .from(V_PROCESS_QUEUE)
                                .where(V_PROCESS_QUEUE.ORG_ID.in(orgIds)
                                        .and(V_PROCESS_QUEUE.CURRENT_STATUS.in(statuses))
                                        .and(V_PROCESS_QUEUE.LAST_UPDATED_AT.greaterOrEqual(fromUpdatedAt)))
                            .groupBy(V_PROCESS_QUEUE.ORG_NAME, V_PROCESS_QUEUE.PROJECT_NAME);

                Result<Record3<String, String, Integer>> r = tx.select(a.field(0, String.class), a.field(1, String.class), a.field(2, Integer.class))
                        .from(a)
                        .where(a.field(rnField).lessOrEqual(maxProjectRows))
                        .fetch();

                Map<String, List<ProjectProcesses>> result = new HashMap<>();
                r.forEach(i -> {
                    String orgName = i.value1();
                    String projectName = i.value2();
                    int count = i.value3();
                    result.computeIfAbsent(orgName, (k) -> new ArrayList<>()).add(new ProjectProcesses(projectName, count));
                });
                return result;
            }
        }
    }
}
