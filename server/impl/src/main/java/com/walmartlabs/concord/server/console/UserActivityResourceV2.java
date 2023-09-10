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
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.*;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;

@Path("/api/v2/service/console/user")
public class UserActivityResourceV2 implements Resource {

    private final ProcessQueueDao processDao;
    private final UserActivityDao dao;

    @Inject
    public UserActivityResourceV2(ProcessQueueDao processDao,
                                  UserActivityDao dao) {
        this.processDao = processDao;
        this.dao = dao;
    }

    @GET
    @Path("/activity")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public UserActivityResponse activity(@QueryParam("maxOwnProcesses") @DefaultValue("5") int maxOwnProcesses) {

        UserPrincipal user = UserPrincipal.assertCurrent();

        ProcessFilter filter = ProcessFilter.builder()
                .initiator(user.getUsername())
                .includeWithoutProject(true)
                .limit(maxOwnProcesses)
                .build();
        List<ProcessEntry> lastProcesses = processDao.list(filter);

        return new UserActivityResponse(null, null, lastProcesses);
    }

    @GET
    @Path("/process-card")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessCardEntry> processCardsList() {

        UserPrincipal user = UserPrincipal.assertCurrent();

        return dao.listCards(user.getId());
    }

    public static class UserActivityDao extends AbstractDao {

        @Inject
        protected UserActivityDao(@MainDB Configuration cfg) {
            super(cfg);
        }

        public List<ProcessCardEntry> listCards(UUID userId) {
            return txResult(tx -> listCards(tx, userId));
        }

        public List<ProcessCardEntry> listCards(DSLContext tx, UUID userId) {
            // TODO: V_USER_TEAMS
            SelectConditionStep<Record1<UUID>> userTeams = tx.select(USER_TEAMS.TEAM_ID)
                    .from(USER_TEAMS)
                    .where(USER_TEAMS.USER_ID.eq(userId));

            SelectConditionStep<Record1<UUID>> byUserFilter = tx.select(USER_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(USER_UI_PROCESS_CARDS)
                    .where(USER_UI_PROCESS_CARDS.USER_ID.eq(userId));

            SelectConditionStep<Record1<UUID>> byTeamFilter = tx.select(TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(TEAM_UI_PROCESS_CARDS)
                    .where(TEAM_UI_PROCESS_CARDS.TEAM_ID.in(userTeams));

            SelectConditionStep<Record11<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[]>> query = tx.select(
                    UI_PROCESS_CARDS.UI_PROCESS_CARD_ID,
                    PROJECTS.ORG_ID,
                    ORGANIZATIONS.ORG_NAME,
                    UI_PROCESS_CARDS.PROJECT_ID,
                    PROJECTS.PROJECT_NAME,
                    UI_PROCESS_CARDS.REPO_ID,
                    REPOSITORIES.REPO_NAME,
                    UI_PROCESS_CARDS.NAME,
                    UI_PROCESS_CARDS.ENTRY_POINT,
                    UI_PROCESS_CARDS.DESCRIPTION,
                    UI_PROCESS_CARDS.ICON)
                    .from(UI_PROCESS_CARDS)
                    .join(REPOSITORIES, JoinType.JOIN).on(REPOSITORIES.REPO_ID.eq(UI_PROCESS_CARDS.REPO_ID))
                    .join(PROJECTS, JoinType.JOIN).on(PROJECTS.PROJECT_ID.eq(UI_PROCESS_CARDS.PROJECT_ID))
                    .join(ORGANIZATIONS, JoinType.JOIN).on(ORGANIZATIONS.ORG_ID.eq(PROJECTS.ORG_ID))
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.in(byUserFilter)
                            .or(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.in(byTeamFilter)));

            return query.fetch(this::toEntry);
        }

        private ProcessCardEntry toEntry(Record11<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[]> r) {
            return ProcessCardEntry.builder()
                    .id(r.get(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID))
                    .orgName(r.get(ORGANIZATIONS.ORG_NAME))
                    .projectName(r.get(PROJECTS.PROJECT_NAME))
                    .repoName(r.get(REPOSITORIES.REPO_NAME))
                    .entryPoint(r.get(UI_PROCESS_CARDS.ENTRY_POINT))
                    .name(r.get(UI_PROCESS_CARDS.NAME))
                    .description(r.get(UI_PROCESS_CARDS.DESCRIPTION))
                    .icon(encodeBase64(r.get(UI_PROCESS_CARDS.ICON)))
                    .build();
        }

        static String encodeBase64(byte[] value) {
            if (value == null) {
                return null;
            }

            return Base64.getEncoder().encodeToString(value);
        }
    }
}
