package com.walmartlabs.concord.server.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.jooq.tables.UiProcessCards;
import com.walmartlabs.concord.server.jooq.tables.Users;
import com.walmartlabs.concord.server.jooq.tables.records.UiProcessCardsRecord;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.*;

import javax.inject.Inject;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static com.walmartlabs.concord.server.jooq.tables.Organizations.ORGANIZATIONS;
import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static com.walmartlabs.concord.server.jooq.tables.Users.USERS;
import static org.jooq.impl.DSL.*;

public class ProcessCardManager {

    private final Dao dao;

    @Inject
    public ProcessCardManager(Dao dao) {
        this.dao = dao;
    }

    public ProcessCardEntry get(UUID cardId) {
        return dao.get(cardId);
    }

    public void delete(UUID cardId) {
        dao.delete(cardId);
    }

    public void assertAccess(UUID cardId) {
        if (Roles.isAdmin()) {
            return;
        }

        UserPrincipal principal = UserPrincipal.assertCurrent();

        EntityOwner owner = dao.getOwner(cardId);
        if (ResourceAccessUtils.isSame(principal, owner)) {
            return;
        }

        throw new UnauthorizedException("The current user (" + principal.getUsername() + ") doesn't have " +
                "access to the process card: " + cardId);
    }

    public void updateAccess(UUID cardId, List<UUID> teamIds, List<UUID> userIds) {
        dao.tx(tx -> {
            dao.rewriteTeamAccess(tx, cardId, teamIds);
            dao.rewriteUserAccess(tx, cardId, userIds);
        });
    }

    public List<ProcessCardEntry> listUserCards(UUID userId) {
        return dao.listCards(userId);
    }

    public ProcessCardOperationResponse createOrUpdate(UUID id, UUID projectId, UUID repoId, String name, Optional<String> entryPoint, String description, InputStream icon, InputStream form, Map<String, Object> data, UUID orderId) {
        boolean exists;
        if (id == null) {
            if (projectId == null) {
                throw new ConcordApplicationException("projectId or projectName is required");
            }
            if (name == null) {
                throw new ConcordApplicationException("name is required");
            }
            id = dao.getIdByName(projectId, name).orElse(null);
            exists = id != null;
        } else {
            exists = dao.get(id) != null;
        }

        if (!exists) {
            UUID resultId = dao.insert(id, projectId, repoId, name, entryPoint.orElse(Constants.Request.DEFAULT_ENTRY_POINT_NAME), description, icon, form, data, orderId);
            return new ProcessCardOperationResponse(resultId, OperationResult.CREATED);
        } else {
            assertAccess(id);

            dao.update(id, projectId, repoId, name, entryPoint.orElse(null), description, icon, form, data, orderId);
            return new ProcessCardOperationResponse(id, OperationResult.UPDATED);
        }
    }

    public <T> Optional<T> getForm(UUID cardId, Function<InputStream, Optional<T>> converter) {
        return dao.getForm(cardId, converter);
    }

    public Map<String, Object> getFormData(UUID cardId) {
        return dao.getFormData(cardId);
    }

    @SuppressWarnings("resource")
    public static class Dao extends AbstractDao {

        private final ConcordObjectMapper objectMapper;

        @Inject
        protected Dao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
            super(cfg);
            this.objectMapper = objectMapper;
        }

        protected void tx(Tx t) {
            super.tx(t);
        }

        public ProcessCardEntry get(UUID cardId) {
            return txResult(tx -> get(tx, cardId));
        }

        private ProcessCardEntry get(DSLContext tx, UUID cardId) {
            return buildSelect(tx)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .fetchOne(this::toEntry);
        }

        public Optional<UUID> getIdByName(UUID projectId, String name) {
            return dsl().select(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.PROJECT_ID.eq(projectId)
                            .and(UI_PROCESS_CARDS.NAME.eq(name)))
                    .fetchOptional(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID);
        }

        public UUID insert(UUID cardId, UUID projectId, UUID repoId, String name, String entryPoint, String description, InputStream icon, InputStream form, Map<String, Object> data, UUID objectId) {
            return txResult(tx -> {
                String sql = tx.insertInto(UI_PROCESS_CARDS)
                        .columns(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID,
                                UI_PROCESS_CARDS.PROJECT_ID,
                                UI_PROCESS_CARDS.REPO_ID,
                                UI_PROCESS_CARDS.NAME,
                                UI_PROCESS_CARDS.ENTRY_POINT,
                                UI_PROCESS_CARDS.DESCRIPTION,
                                UI_PROCESS_CARDS.ICON,
                                UI_PROCESS_CARDS.FORM,
                                UI_PROCESS_CARDS.DATA,
                                UI_PROCESS_CARDS.OWNER_ID,
                                UI_PROCESS_CARDS.ORDER_ID)
                        .values((UUID) null, null, null, null, null, null, null, null, null, null, null)
                        .returning(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                        .getSQL();

                return tx.connectionResult(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setObject(1, cardId == null ? UUID.randomUUID() : cardId);
                        ps.setObject(2, projectId);
                        ps.setObject(3, repoId);
                        ps.setString(4, name);
                        ps.setString(5, entryPoint);
                        ps.setString(6, description);
                        ps.setBinaryStream(7, icon);
                        ps.setBinaryStream(8, form);
                        ps.setObject(9, data != null ? objectMapper.toJSONB(data).data() : null);
                        ps.setObject(10, UserPrincipal.assertCurrent().getId());

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new RuntimeException("Can't insert process card");
                            }

                            return UUID.fromString(rs.getString(1));
                        }
                    }
                });
            });
        }

        public void update(UUID cardId, UUID projectId, UUID repoId, String name,
                           String entryPoint, String description, InputStream icon, InputStream form,
                           Map<String, Object> data, UUID orderId) {
            tx(tx -> {
                List<Object> params = new ArrayList<>();
                UpdateSetStep<UiProcessCardsRecord> q = tx.update(UI_PROCESS_CARDS);

                if (projectId != null) {
                    q.set(UI_PROCESS_CARDS.PROJECT_ID, (UUID) null);
                    params.add(projectId);
                }

                if (repoId != null) {
                    q.set(UI_PROCESS_CARDS.REPO_ID, (UUID) null);
                    params.add(repoId);
                }

                if (name != null) {
                    q.set(UI_PROCESS_CARDS.NAME, (String) null);
                    params.add(name);
                }

                if (entryPoint != null) {
                    q.set(UI_PROCESS_CARDS.ENTRY_POINT, (String) null);
                    params.add(entryPoint);
                }

                if (description != null) {
                    q.set(UI_PROCESS_CARDS.DESCRIPTION, (String) null);
                    params.add(description);
                }

                if (icon != null) {
                    q.set(UI_PROCESS_CARDS.ICON, (byte[]) null);
                    params.add(icon);
                }

                if (form != null) {
                    q.set(UI_PROCESS_CARDS.FORM, (byte[]) null);
                    params.add(form);
                }

                if (data != null) {
                    q.set(UI_PROCESS_CARDS.DATA, (JSONB) null);
                    params.add(objectMapper.toJSONB(data).data());
                }

                if (params.isEmpty()) {
                    return;
                }

                if (orderId != null) {
                    q.set(UI_PROCESS_CARDS.ORDER_ID, (UUID) null);
                    params.add(orderId);
                }

                String sql = q.set(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID, cardId)
                        .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                        .getSQL();
                params.add(cardId);
                params.add(cardId);

                tx.connection(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < params.size(); i++) {
                            Object p = params.get(i);
                            if (p instanceof InputStream) {
                                ps.setBinaryStream(i + 1, (InputStream) p);
                            } else {
                                ps.setObject(i + 1, p);
                            }
                        }

                        ps.executeUpdate();
                    }
                });
            });
        }

        public void delete(UUID id) {
            tx(tx -> tx.delete(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(id))
                    .execute());
        }

        public void rewriteTeamAccess(DSLContext tx, UUID cardId, List<UUID> teamIds) {
            tx.delete(TEAM_UI_PROCESS_CARDS)
                    .where(TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .execute();

            for (UUID teamId : teamIds) {
                tx.insertInto(TEAM_UI_PROCESS_CARDS)
                        .columns(TEAM_UI_PROCESS_CARDS.TEAM_ID, TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                        .values(teamId, cardId)
                        .execute();
            }
        }

        public void rewriteUserAccess(DSLContext tx, UUID cardId, List<UUID> userIds) {
            tx.delete(USER_UI_PROCESS_CARDS)
                    .where(USER_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .execute();

            for (UUID userId : userIds) {
                tx.insertInto(USER_UI_PROCESS_CARDS)
                        .columns(USER_UI_PROCESS_CARDS.USER_ID, USER_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                        .values(userId, cardId)
                        .execute();
            }
        }

        public EntityOwner getOwner(UUID cardId) {
            Users u = USERS.as("u");
            UiProcessCards c = UI_PROCESS_CARDS.as("c");

            return txResult(tx -> select(
                    c.OWNER_ID,
                    u.USER_ID,
                    u.USERNAME,
                    u.DOMAIN,
                    u.DISPLAY_NAME,
                    u.USER_TYPE)
                    .from(c)
                    .leftJoin(u).on(u.USER_ID.eq(c.OWNER_ID))
                    .where(c.UI_PROCESS_CARD_ID.eq(cardId))
                    .fetchOne(r -> toOwner(r.get(c.OWNER_ID), r.get(u.USERNAME), r.get(u.DOMAIN), r.get(u.DISPLAY_NAME), r.get(u.USER_TYPE))));
        }

        public List<ProcessCardEntry> listCards(UUID userId) {
            return txResult(tx -> listCards(tx, userId));
        }

        private List<ProcessCardEntry> listCards(DSLContext tx, UUID userId) {
            var userTeams = tx.select(V_USER_TEAMS.TEAM_ID)
                    .from(V_USER_TEAMS)
                    .where(V_USER_TEAMS.USER_ID.eq(userId));

            var byUserFilter = tx.select(USER_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(USER_UI_PROCESS_CARDS)
                    .where(USER_UI_PROCESS_CARDS.USER_ID.eq(userId));

            var byTeamFilter = tx.select(TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(TEAM_UI_PROCESS_CARDS)
                    .where(TEAM_UI_PROCESS_CARDS.TEAM_ID.in(userTeams));

            var byOwnerFilter = tx.select(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.OWNER_ID.isNotNull().and(UI_PROCESS_CARDS.OWNER_ID.eq(userId)));

            var userCards = byUserFilter
                    .unionAll(byTeamFilter)
                    .unionAll(byOwnerFilter);

            var userCardsFilter = tx.select(userCards.field(TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID))
                    .from(userCards);

            var query = buildSelect(tx)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.in(userCardsFilter));

            return query
                    .orderBy(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .fetch(this::toEntry);
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

        public Map<String, Object> getFormData(UUID cardId) {
            return txResult(tx -> getFormData(tx, cardId));
        }

        public Map<String, Object> getFormData(DSLContext tx, UUID cardId) {
            return tx.select(UI_PROCESS_CARDS.DATA)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .fetchOne(r -> objectMapper.fromJSONB(r.get(UI_PROCESS_CARDS.DATA)));
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

        private static SelectOnConditionStep<Record13<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[], Boolean, UUID>> buildSelect(DSLContext tx) {
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
                            isCustomForm.as("isCustomForm"),
                            UI_PROCESS_CARDS.ORDER_ID)
                    .from(UI_PROCESS_CARDS)
                    .leftJoin(REPOSITORIES).on(REPOSITORIES.REPO_ID.eq(UI_PROCESS_CARDS.REPO_ID))
                    .leftJoin(PROJECTS).on(PROJECTS.PROJECT_ID.eq(UI_PROCESS_CARDS.PROJECT_ID))
                    .leftJoin(ORGANIZATIONS).on(ORGANIZATIONS.ORG_ID.eq(PROJECTS.ORG_ID));
        }

        private ProcessCardEntry toEntry(Record13<UUID, UUID, String, UUID, String, UUID, String, String, String, String, byte[], Boolean, UUID> r) {
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
                    .orderId(r.get(UI_PROCESS_CARDS.ORDER_ID))
                    .build();
        }

        static String encodeBase64(byte[] value) {
            if (value == null) {
                return null;
            }

            return Base64.getEncoder().encodeToString(value);
        }

        private static EntityOwner toOwner(UUID id, String username, String domain, String displayName, String userType) {
            if (id == null) {
                return null;
            }
            return EntityOwner.builder()
                    .id(id)
                    .username(username)
                    .userDomain(domain)
                    .displayName(displayName)
                    .userType(UserType.valueOf(userType))
                    .build();
        }
    }
}
