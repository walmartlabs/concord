package com.walmartlabs.concord.server.console;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.GenericOperationResult;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.jooq.tables.records.UiProcessCardsRecord;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.UpdateSetFirstStep;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.TEAM_UI_PROCESS_CARDS;
import static com.walmartlabs.concord.server.jooq.Tables.UI_PROCESS_CARDS;
import static org.jooq.impl.DSL.value;

@Named
@Singleton
@javax.ws.rs.Path("/api/v1/org/")
public class ProcessCardResource implements Resource {

    private final OrganizationDao orgDao;
    private final ProjectDao projectDao;
    private final RepositoryDao repositoryDao;
    private final Dao dao;

    @Inject
    public ProcessCardResource(OrganizationDao orgDao,
                               ProjectDao projectDao,
                               RepositoryDao repositoryDao,
                               Dao dao) {

        this.orgDao = orgDao;
        this.projectDao = projectDao;
        this.repositoryDao = repositoryDao;
        this.dao = dao;
    }

    @POST
    @Path("/{orgName}/process-card")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public GenericOperationResult createOrUpdate(@PathParam("orgName") @ConcordKey String orgName,
                                                 MultipartInput input) throws IOException {
        UUID orgId = assertOrg(orgName);
        UUID projectId = getProject(input, orgId);
        UUID repoId = getRepo(input, projectId);
        String name = MultipartUtils.getString(input, "name");
        String entryPoint = MultipartUtils.getString(input, "entryPoint");
        String description = MultipartUtils.getString(input, "description");

        byte[] icon = getIcon(input);
        byte[] form = getForm(input);

        Map<String, Object> data = MultipartUtils.getMap(input, "data");

        UUID id = MultipartUtils.getUuid(input, "id");
        boolean exists = false;
        if (id != null) {
            exists = dao.exists(id);
        }

        List<UUID> teamIds = MultipartUtils.getUUIDList(input, "teamIds");

        if (!exists) {
            dao.tx(tx -> {
                UUID resultId = dao.insert(tx, id, projectId, repoId, name, entryPoint, description, icon, form, data);
                for (UUID teamId : teamIds) {
                    dao.upsertTeamAccess(tx, resultId, teamId);
                }
            });
            return new GenericOperationResult(OperationResult.CREATED);
        } else {
            dao.tx(tx -> {
                dao.update(id, projectId, repoId, name, entryPoint, description, icon, form, data);
                for (UUID teamId : teamIds) {
                    dao.upsertTeamAccess(tx, id, teamId);
                }
            });
            return new GenericOperationResult(OperationResult.UPDATED);
        }
    }

    private byte[] getIcon(MultipartInput input) throws IOException {
        try (InputStream is = MultipartUtils.getStream(input, "icon")) {
            if (is != null) {
                return IOUtils.toByteArray(is);
            }
        }
        return null;
    }

    private byte[] getForm(MultipartInput input) throws IOException {
        try (InputStream is = MultipartUtils.getStream(input, "form")) {
            if (is != null) {
                return IOUtils.toByteArray(is);
            }
        }
        return null;
    }

    private UUID assertOrg(String name) {
        UUID result = orgDao.getId(name);
        if (result != null) {
            return result;
        }
        throw new ValidationErrorsException("Organization not found: " + name);
    }

    private UUID getProject(MultipartInput input, UUID orgId) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME);
        if (id == null && name != null) {
            if (orgId == null) {
                throw new ValidationErrorsException("Organization ID or name is required");
            }

            id = projectDao.getId(orgId, name);
            if (id == null) {
                throw new ValidationErrorsException("Project not found: " + name);
            }
        }
        return id;
    }

    private UUID getRepo(MultipartInput input, UUID projectId) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.REPO_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.REPO_NAME);
        if (id == null && name != null) {
            if (projectId == null) {
                throw new ValidationErrorsException("Project ID or name is required");
            }

            id = repositoryDao.getId(projectId, name);
            if (id == null) {
                throw new ValidationErrorsException("Repository not found: " + name);
            }
        }
        return id;
    }

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

        public UUID insert(UUID cardId, UUID projectId, UUID repoId, String name, String entryPoint, String description, byte[] icon, byte[] form, Map<String, Object> data) {
            return txResult(tx -> insert(tx, cardId, projectId, repoId, name, entryPoint, description, icon, form, data));
        }

        private UUID insert(DSLContext tx, UUID cardId, UUID projectId, UUID repoId, String name, String entryPoint, String description, byte[] icon, byte[] form, Map<String, Object> data) {
            return tx.insertInto(UI_PROCESS_CARDS)
                    .columns(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID,
                            UI_PROCESS_CARDS.PROJECT_ID,
                            UI_PROCESS_CARDS.REPO_ID,
                            UI_PROCESS_CARDS.NAME,
                            UI_PROCESS_CARDS.ENTRY_POINT,
                            UI_PROCESS_CARDS.DESCRIPTION,
                            UI_PROCESS_CARDS.ICON,
                            UI_PROCESS_CARDS.FORM,
                            UI_PROCESS_CARDS.DATA)
                    .values(value(cardId == null ? UUID.randomUUID() : cardId),
                            value(projectId),
                            value(repoId),
                            value(name),
                            value(entryPoint),
                            value(description),
                            value(icon),
                            value(form),
                            value(objectMapper.toJSONB(data)))
                    .returning(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .fetchOne()
                    .getUiProcessCardId();
        }

        public void update(UUID cardId, UUID projectId, UUID repoId, String name,
                           String entryPoint, String description, byte[] icon, byte[] form, Map<String, Object> data) {
            tx(tx -> update(tx, cardId, projectId, repoId, name, entryPoint, description, icon, form, data));
        }

        public void update(DSLContext tx, UUID cardId, UUID projectId, UUID repoId, String name,
                           String entryPoint, String description, byte[] icon, byte[] form, Map<String, Object> data) {

            UpdateSetFirstStep<UiProcessCardsRecord> q = tx.update(UI_PROCESS_CARDS);

            if (projectId != null) {
                q.set(UI_PROCESS_CARDS.PROJECT_ID, projectId);
            }

            if (repoId != null) {
                q.set(UI_PROCESS_CARDS.REPO_ID, repoId);
            }

            if (name != null) {
                q.set(UI_PROCESS_CARDS.NAME, name);
            }

            if (entryPoint != null) {
                q.set(UI_PROCESS_CARDS.ENTRY_POINT, entryPoint);
            }

            if (description != null) {
                q.set(UI_PROCESS_CARDS.DESCRIPTION, description);
            }

            if (icon != null) {
                q.set(UI_PROCESS_CARDS.ICON, icon);
            }

            if (form != null) {
                q.set(UI_PROCESS_CARDS.FORM, form);
            }

            if (data != null) {
                q.set(UI_PROCESS_CARDS.DATA, objectMapper.toJSONB(data));
            }

            q.set(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID, UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(cardId))
                    .execute();
        }

        public boolean exists(UUID id) {
            return txResult(tx -> tx.select(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .from(UI_PROCESS_CARDS)
                    .where(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID.eq(id))
                    .fetchOne(UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)) != null;
        }

        public void upsertTeamAccess(UUID id, UUID teamId) {
            tx(tx -> upsertTeamAccess(tx, id, teamId));
        }

        public void upsertTeamAccess(DSLContext tx, UUID id, UUID teamId) {
            tx.insertInto(TEAM_UI_PROCESS_CARDS)
                    .columns(TEAM_UI_PROCESS_CARDS.TEAM_ID, TEAM_UI_PROCESS_CARDS.UI_PROCESS_CARD_ID)
                    .values(teamId, id)
                    .onDuplicateKeyIgnore()
                    .execute();
        }
    }
}
