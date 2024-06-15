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

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;
import static org.jooq.impl.DSL.*;

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

    @Override
    protected <T> T txResult(TxResult<T> t) {
        return super.txResult(t);
    }

    public UUID getId(UUID projectId, String repoName) {
        return dsl().select(REPOSITORIES.REPO_ID)
                .from(REPOSITORIES)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                        .and(REPOSITORIES.REPO_NAME.eq(repoName)))
                .fetchOne(REPOSITORIES.REPO_ID);
    }

    public UUID getProjectId(UUID repoId) {
        return dsl().select(REPOSITORIES.PROJECT_ID)
                .from(REPOSITORIES)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .fetchOne(REPOSITORIES.PROJECT_ID);
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

    public RepositoryEntry get(UUID projectId, String repoName) {
        return txResult(tx -> get(tx, projectId, repoName));
    }

    public RepositoryEntry get(DSLContext tx, UUID projectId, String repoName) {
        return selectRepositoryEntry(tx)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId)
                        .and(REPOSITORIES.REPO_NAME.eq(repoName)))
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

    public UUID insert(UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled, Map<String, Object> meta, boolean isTriggersDisabled) {
        return txResult(tx -> insert(tx, projectId, repositoryName, url, branch, commitId, path, secretId, disabled, meta, isTriggersDisabled));
    }

    public UUID insert(DSLContext tx, UUID projectId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled, Map<String, Object> meta, boolean isTriggersDisabled) {
        return tx.insertInto(REPOSITORIES)
                .columns(REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_NAME,
                        REPOSITORIES.REPO_URL, REPOSITORIES.REPO_BRANCH, REPOSITORIES.REPO_COMMIT_ID,
                        REPOSITORIES.REPO_PATH, REPOSITORIES.SECRET_ID, REPOSITORIES.META, REPOSITORIES.IS_DISABLED, REPOSITORIES.IS_TRIGGERS_DISABLED)
                .values(projectId, repositoryName, url, branch, commitId, path, secretId, objectMapper.toJSONB(meta), disabled, isTriggersDisabled)
                .returning(REPOSITORIES.REPO_ID)
                .fetchOne()
                .getRepoId();
    }

    public void update(DSLContext tx, UUID repoId, String repositoryName, String url, String branch, String commitId, String path, UUID secretId, boolean disabled, boolean isTriggersDisabled) {
        int i = tx.update(REPOSITORIES)
                .set(REPOSITORIES.REPO_NAME, repositoryName)
                .set(REPOSITORIES.REPO_URL, url)
                .set(REPOSITORIES.SECRET_ID, secretId)
                .set(REPOSITORIES.REPO_BRANCH, branch)
                .set(REPOSITORIES.REPO_COMMIT_ID, commitId)
                .set(REPOSITORIES.REPO_PATH, path)
                .set(REPOSITORIES.IS_DISABLED, disabled)
                .set(REPOSITORIES.IS_TRIGGERS_DISABLED, isTriggersDisabled)
                .where(REPOSITORIES.REPO_ID.eq(repoId))
                .execute();

        if (i != 1) {
            throw new DataAccessException("Invalid number of rows: " + i);
        }
    }

    public void disable(DSLContext tx, UUID repoId) {
        tx.update(REPOSITORIES)
            .set(REPOSITORIES.IS_DISABLED, true)
            .where(REPOSITORIES.REPO_ID.eq(repoId))
            .execute();
    }

    public void clearSecretMappingBySecretId(DSLContext tx, UUID secretId) {
        tx.update(REPOSITORIES)
                .setNull(REPOSITORIES.SECRET_ID)
                .where(REPOSITORIES.SECRET_ID.eq(secretId))
                .execute();
    }

    public void clearSecretMappingByProjectId(DSLContext tx, UUID projectId) {
        tx.update(REPOSITORIES)
                .setNull(REPOSITORIES.SECRET_ID)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId))
                .execute();
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
        return selectRepositoryEntry(dsl())
                .fetch(this::toEntry);
    }

    public List<RepositoryEntry> list(UUID projectId) {
        return txResult(tx -> list(tx, projectId));
    }

    public List<RepositoryEntry> list(DSLContext tx, UUID projectId) {
        SelectConditionStep<Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean>> query = selectRepositoryEntry(tx)
                .where(REPOSITORIES.PROJECT_ID.eq(projectId));
        return query.fetch(this::toEntry);
    }

    public List<RepositoryEntry> list(UUID projectId, Field<?> sortField,
                                      boolean asc, int offset, int limit,
                                      String filter) {

        SelectConditionStep<Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean>> q = selectRepositoryEntry(dsl())
                .where(REPOSITORIES.PROJECT_ID.eq(projectId));

        if (sortField != null) {
            q.orderBy(asc ? sortField.asc() : sortField.desc());
        }

        if (filter != null) {
            q.and(REPOSITORIES.REPO_NAME.containsIgnoreCase(filter));
        }

        if (offset > 0) {
            q.offset(offset);
        }

        if (limit > 0) {
            q.limit(limit);
        }

        return q.fetch(this::toEntry);
    }

    public List<RepositoryEntry> find(String repoUrl) {
        return find(null, repoUrl);
    }

    public List<RepositoryEntry> find(UUID projectId, String repoUrl) {
        SelectConditionStep<Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean>> select = selectRepositoryEntry(dsl())
                .where(REPOSITORIES.REPO_URL.contains(repoUrl));

        if (projectId != null) {
            select.and(REPOSITORIES.PROJECT_ID.eq(projectId));
        }

        return select.fetch(this::toEntry);
    }

    public List<RepositoryEntry> findSimilar(String repoUrlPattern) {
        return findSimilar(null, repoUrlPattern);
    }

    public List<RepositoryEntry> findSimilar(UUID projectId, String pattern) {
        SelectConditionStep<Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean>> select = selectRepositoryEntry(dsl())
                .where(REPOSITORIES.REPO_URL.similarTo(pattern));

        if (projectId != null) {
            select.and(REPOSITORIES.PROJECT_ID.eq(projectId));
        }

        return select.fetch(this::toEntry);
    }

    public void updateMeta(DSLContext tx, UUID id, Map<String, Object> meta) {
        Field<JSONB> updateJson = field(coalesce(REPOSITORIES.META, field("?", JSONB.class, JSONB.valueOf("{}"))) + " || ?::jsonb", JSONB.class, objectMapper.toJSONB(meta));
        Field<JSONB> metaField = function("jsonb_strip_nulls", JSONB.class, updateJson);

        tx.update(REPOSITORIES)
                .set(REPOSITORIES.META, metaField)
                .where(REPOSITORIES.REPO_ID.eq(id))
                .execute();
    }

    private static SelectJoinStep<Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean>> selectRepositoryEntry(DSLContext tx) {
        return tx.select(REPOSITORIES.REPO_ID,
                REPOSITORIES.PROJECT_ID,
                REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                REPOSITORIES.REPO_BRANCH,
                REPOSITORIES.REPO_COMMIT_ID,
                REPOSITORIES.REPO_PATH,
                REPOSITORIES.IS_DISABLED,
                REPOSITORIES.META,
                SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME,
                SECRETS.STORE_TYPE,
                REPOSITORIES.IS_TRIGGERS_DISABLED)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID));
    }

    private RepositoryEntry toEntry(Record13<UUID, UUID, String, String, String, String, String, Boolean, JSONB, UUID, String, String, Boolean> r) {
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
                objectMapper.fromJSONB(r.get(REPOSITORIES.META)),
                r.get(REPOSITORIES.IS_TRIGGERS_DISABLED));
    }
}
