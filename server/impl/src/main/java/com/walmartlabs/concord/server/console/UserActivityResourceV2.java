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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessFilter;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.UserPrincipal;
import org.jooq.*;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.jooq.impl.DSL.*;

@javax.ws.rs.Path("/api/v2/service/console/user")
public class UserActivityResourceV2 implements Resource {

    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private final ProcessQueueDao processDao;
    private final UserActivityDao dao;
    private final ConcordObjectMapper objectMapper;

    @Inject
    public UserActivityResourceV2(ProcessQueueDao processDao,
                                  UserActivityDao dao,
                                  ConcordObjectMapper objectMapper) {
        this.processDao = processDao;
        this.dao = dao;
        this.objectMapper = objectMapper;
    }

    @GET
    @javax.ws.rs.Path("/activity")
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
    @javax.ws.rs.Path("/process-card")
    @Produces(MediaType.APPLICATION_JSON)
    @WithTimer
    public List<ProcessCardEntry> processCardsList() {

        UserPrincipal user = UserPrincipal.assertCurrent();

        return dao.listCards(user.getId());
    }

    @GET
    @javax.ws.rs.Path("/process-card/{cardId}/form")
    @Produces(MediaType.TEXT_HTML)
    @WithTimer
    public Response processForm(@PathParam("cardId") UUID cardId) {

        Optional<Path> o = dao.getForm(cardId, src -> {
            try {
                Path tmp = IOUtils.createTempFile("process-form", ".html");
                Files.copy(src, tmp, StandardCopyOption.REPLACE_EXISTING);
                return Optional.of(tmp);
            } catch (IOException e) {
                throw new ConcordApplicationException("Error while downloading custom process start form: " + cardId, e);
            }
        });

        if (!o.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return toBinaryResponse(o.get());
    }

    @GET
    @javax.ws.rs.Path("/process-card/{cardId}/data.js")
    @Produces("text/javascript")
    @WithTimer
    public Response processFormData(@PathParam("cardId") UUID cardId) {
        ProcessCardEntry card = dao.get(cardId);
        if (card ==null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Map<String, Object> customData = dao.getFormData(cardId);

        Map<String, Object> resultData = new HashMap<>(customData != null ? customData : Collections.emptyMap());
        resultData.put("org", card.orgName());
        resultData.put("project", card.projectName());
        resultData.put("repo", card.repoName());
        resultData.put("entryPoint", card.entryPoint());

        return Response.ok(formatData(resultData))
                .build();
    }

    private String formatData(Map<String, Object> data) {
        return String.format(DATA_FILE_TEMPLATE, objectMapper.toString(data));
    }

    private static Response toBinaryResponse(Path file) {
        return Response.ok((StreamingOutput) out -> {
            try (InputStream in = Files.newInputStream(file)) {
                IOUtils.copy(in, out);
            } finally {
                Files.delete(file);
            }
        }).build();
    }

    public static class UserActivityDao extends AbstractDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        protected UserActivityDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);
            this.objectMapper = objectMapper;
        }

        public ProcessCardEntry get(UUID cardId) {
            return txResult(tx -> get(tx, cardId));
        }

        public ProcessCardEntry get(DSLContext tx, UUID cardId) {
            return buildSelect(tx)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .fetchOne(this::toEntry);
        }

        public List<ProcessCardEntry> listCards(UUID userId) {
            return txResult(tx -> listCards(tx, userId));
        }

        public Map<String, Object> getFormData(UUID cardId) {
            return txResult(tx -> getFormData(tx, cardId));
        }

        public Map<String, Object> getFormData(DSLContext tx, UUID cardId) {
            return tx.select(UI_PROCESS_CARDS.DATA)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .fetchOne(r -> objectMapper.fromJSONB(r.get(UI_PROCESS_CARDS.DATA)));
        }

        public <T> Optional<T> getForm(UUID cardId, Function<InputStream, Optional<T>> converter) {
            return txResult(tx -> getForm(tx, cardId, converter));
        }

        public <T> Optional<T> getForm(DSLContext tx, UUID cardId, Function<InputStream, Optional<T>> converter) {
            String sql = tx.select(UI_PROCESS_CARDS.FORM)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq((UUID) null)
                            .and(UI_PROCESS_CARDS.FORM.isNotNull()))
                    .getSQL();

            return getInputStream(tx, sql, cardId, converter);
        }

        private static <T> Optional<T> getInputStream(DSLContext tx, String sql, UUID cardId, Function<InputStream, Optional<T>> converter) {
            return tx.connectionResult(conn -> {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, cardId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            return Optional.empty();
                        }
                        try (InputStream in = rs.getBinaryStream(1)) {
                            return converter.apply(in);
                        }
                    }
                }
            });
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

            SelectConditionStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[], Boolean>> query =
                    buildSelect(tx)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.in(byUserFilter)
                            .or(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.in(byTeamFilter)));

            return query.fetch(this::toEntry);
        }

        private static SelectOnConditionStep<Record12<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[], Boolean>> buildSelect(DSLContext tx) {

            Field<Boolean> isCustomForm = when(field(UI_PROCESS_CARDS.FORM).isNotNull(), true).otherwise(false);

            return tx.select(
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
                            UI_PROCESS_CARDS.ICON,
                            isCustomForm.as("isCustomForm"))
                    .from(UI_PROCESS_CARDS)
                    .join(REPOSITORIES, JoinType.JOIN).on(REPOSITORIES.REPO_ID.eq(UI_PROCESS_CARDS.REPO_ID))
                    .join(PROJECTS, JoinType.JOIN).on(PROJECTS.PROJECT_ID.eq(UI_PROCESS_CARDS.PROJECT_ID))
                    .join(ORGANIZATIONS, JoinType.JOIN).on(ORGANIZATIONS.ORG_ID.eq(PROJECTS.ORG_ID));
        }

        private ProcessCardEntry toEntry(Record12<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[], Boolean> r) {
            return ProcessCardEntry.builder()
                    .id(r.get(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID))
                    .orgName(r.get(ORGANIZATIONS.ORG_NAME))
                    .projectName(r.get(PROJECTS.PROJECT_NAME))
                    .repoName(r.get(REPOSITORIES.REPO_NAME))
                    .entryPoint(r.get(UI_PROCESS_CARDS.ENTRY_POINT))
                    .name(r.get(UI_PROCESS_CARDS.NAME))
                    .description(r.get(UI_PROCESS_CARDS.DESCRIPTION))
                    .icon(encodeBase64(r.get(UI_PROCESS_CARDS.ICON)))
                    .isCustomForm(r.get("isCustomForm", Boolean.class))
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
