package com.walmartlabs.concord.server.org.project;

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
import com.walmartlabs.concord.server.ConcordObjectMapper;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static org.jooq.impl.DSL.*;

@Named
public class RepositoryDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public RepositoryDao(@MainDB Configuration cfg,
                         ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    @Override
    protected void tx(Tx t) {
        super.tx(t);
    }

    public UUID getId(UUID projectId, String repoName) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(REPOSITORIES.REPO_ID)
                    .from(REPOSITORIES)
                    .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                            .and(REPOSITORIES.REPO_NAME.eq(repoName)))
                    .fetchOne(REPOSITORIES.REPO_ID);
        }
    }

    public UUID getProjectId(UUID repoId) {
        try (DSLContext tx = DSL.using(cfg)) {
            return tx.select(REPOSITORIES.PROJECT_ID)
                    .from(REPOSITORIES)
                    .where(REPOSITORIES.REPO_ID.eq(repoId))
                    .fetchOne(REPOSITORIES.PROJECT_ID);
        }
    }

    public RepositoryEntry get(UUID projectId, UUID repoId) {
        return txResult(tx -> get(tx, projectId, repoId));
    }

    public RepositoryEntry get(DSLContext tx, UUID projectId, UUID repoId) {
        return selectRepositoryEntry(tx)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                        .and(REPOSITORIES.REPO_ID.eq(repoId)))
                .fetchOne(this::toEntry);
    }

    public RepositoryEntry get(UUID repoId) {
        return txResult(tx -> selectRepositoryEntry(tx)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .fetchOne(this::toEntry));
    }

    public RepositoryEntry get(DSLContext tx, UUID repoId) {
        return selectRepositoryEntry(tx)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .fetchOne(this::toEntry);
    }

    public UUID insert(UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled, Map<String, Object> meta) {
        return txResult(tx -> insert(tx, projectId, repositoryName, url, branch, commitId, path, secretId, disabled, meta));
    }

    public UUID insert(DSLContext tx, UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled, Map<String, Object> meta) {
        return tx.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_NAME,
                        REPOSITORIES.REPO_URL, REPOSITORIES.REPO_BRANCH, REPOSITORIES.REPO_COMMIT_ID,
                        REPOSITORIES.REPO_PATH, REPOSITORIES.SECRET_ID, REPOSITORIES.META, REPOSITORIES.IS_DISABLED)
                .values(projectId, repositoryName, url, branch, commitId, path, secretId, field("?::jsonb", objectMapper.serialize(meta)), disabled)
                .returning(REPOSITORIES.REPO_ID)
                .fetchOne()
                .getRepoId();
    }

    public void update(DSLContext tx, UUID repoId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled) {
        int i = tx.update(REPOSITORIES)
                .set(REPOSITORIES.REPO_NAME, repositoryName)
                .set(REPOSITORIES.REPO_URL, url)
                .set(REPOSITORIES.SECRET_ID, secretId)
                .set(REPOSITORIES.REPO_BRANCH, branch)
                .set(REPOSITORIES.REPO_COMMIT_ID, commitId)
                .set(REPOSITORIES.REPO_PATH, path)
                .set(REPOSITORIES.IS_DISABLED, disabled)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows: " + i);
        }
    }

    public void delete(UUID repoId) {
        tx(tx -> delete(tx, repoId));
    }

    public void delete(DSLContext tx, UUID repoId) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .execute();
    }

    public void deleteAll(DSLContext tx, UUID projectId) {
        tx.deleteFrom(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                .execute();
    }

    public List<RepositoryEntry> list() {
        try (DSLContext tx = DSL.using(cfg)) {
            return selectRepositoryEntry(tx)
                    .fetch(this::toEntry);
        }
    }

    public List<RepositoryEntry> list(UUID projectId) {
        return list(projectId, null, false);
    }

    public List<RepositoryEntry> list(DSLContext tx, UUID projectId) {
        return list(tx, projectId, null, false);
    }

    public List<RepositoryEntry> list(UUID projectId, Field<?> sortField, boolean asc) {
        try (DSLContext tx = DSL.using(cfg)) {
            return list(tx, projectId, sortField, asc);
        }
    }

    public List<RepositoryEntry> list(DSLContext tx, UUID projectId, Field<?> sortField, boolean asc) {
        SelectConditionStep<Record12<UUID, UUID, String, String, String, String, String, Boolean, String, UUID, String, String>> query = selectRepositoryEntry(tx)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId));

        if (sortField != null) {
            query.orderBy(asc ? sortField.asc() : sortField.desc());
        }

        return query.fetch(this::toEntry);
    }

    public List<RepositoryEntry> find(UUID projectId, String repoUrl) {
        try (DSLContext tx = DSL.using(cfg)) {
            SelectConditionStep<Record12<UUID, UUID, String, String, String, String, String, Boolean, String, UUID, String, String>> select = selectRepositoryEntry(tx)
                    .where(REPOSITORIES.REPO_URL.contains(repoUrl));

            if (projectId != null) {
                select.and(REPOSITORIES.PROJECT_ID.eq(projectId));
            }

            return select.fetch(this::toEntry);
        }
    }

    public void updateMeta(DSLContext tx, UUID id, Map<String, Object> meta) {
        Field<String> updateJson = field(coalesce(REPOSITORIES.META, field("?::jsonb", String.class, "{}")) + " || ?::jsonb", String.class, objectMapper.serialize(meta));
        Field<Object> metaField = function("jsonb_strip_nulls", Object.class, updateJson);

        tx.update(REPOSITORIES)
                .set(REPOSITORIES.META, (Object) metaField)
                .where(REPOSITORIES.REPO_ID.eq(id))
                .execute();
    }

    private static SelectJoinStep<Record12<UUID, UUID, String, String, String, String, String, Boolean, String, UUID, String, String>> selectRepositoryEntry(DSLContext tx) {
        return tx.select(REPOSITORIES.REPO_ID,
                REPOSITORIES.PROJECT_ID,
                REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                REPOSITORIES.REPO_BRANCH,
                REPOSITORIES.REPO_COMMIT_ID,
                REPOSITORIES.REPO_PATH,
                REPOSITORIES.IS_DISABLED,
                REPOSITORIES.META.cast(String.class),
                SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME,
                SECRETS.STORE_TYPE)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID));
    }

    private RepositoryEntry toEntry(Record12<UUID, UUID, String, String, String, String, String, Boolean, String, UUID, String, String> r) {
        return new RepositoryEntry(r.get(REPOSITORIES.REPO_ID),
                r.get(REPOSITORIES.PROJECT_ID),
                r.get(REPOSITORIES.REPO_NAME),
                r.get(REPOSITORIES.REPO_URL),
                r.get(REPOSITORIES.REPO_BRANCH),
                r.get(REPOSITORIES.REPO_COMMIT_ID),
                r.get(REPOSITORIES.REPO_PATH),
                r.get(REPOSITORIES.IS_DISABLED),
                r.get(SECRETS.SECRET_ID),
                r.get(SECRETS.SECRET_NAME),
                r.get(SECRETS.STORE_TYPE),
                objectMapper.deserialize(r.get(REPOSITORIES.META.cast(String.class))));
    }
}
