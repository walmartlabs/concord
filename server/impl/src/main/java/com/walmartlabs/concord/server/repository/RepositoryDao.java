package com.walmartlabs.concord.server.repository;

import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.server.api.IdName;
import com.walmartlabs.concord.server.api.repository.RepositoryEntry;
import org.jooq.*;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

import static com.walmartlabs.concord.server.jooq.public_.tables.Repositories.REPOSITORIES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Secrets.SECRETS;

@Named
public class RepositoryDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(RepositoryDao.class);

    @Inject
    public RepositoryDao(Configuration cfg) {
        super(cfg);
    }

    public String getName(String id) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(REPOSITORIES.REPO_NAME)
                    .from(REPOSITORIES)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .fetchOne(REPOSITORIES.REPO_NAME);
        }
    }

    public RepositoryEntry getByName(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            RepositoryEntry e = selectRepositoryEntry(create)
                    .where(REPOSITORIES.REPO_NAME.eq(name))
                    .fetchOne(RepositoryDao::toEntry);

            if (e == null) {
                log.info("get ['{}'] -> not found", name);
                return null;
            }

            log.info("get ['{}'] -> found: {}", name, e.getId());
            return e;
        }
    }

    public void insert(String projectId, String id, String name, String url, String secretId) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.insertInto(REPOSITORIES)
                    .columns(REPOSITORIES.REPO_ID, REPOSITORIES.PROJECT_ID, REPOSITORIES.REPO_NAME,
                            REPOSITORIES.REPO_URL, REPOSITORIES.SECRET_ID)
                    .values(id, projectId, name, url, secretId)
                    .execute();
        });
        log.info("insert ['{}', '{}', '{}', '{}', '{}'] -> done", projectId, id, name, url, secretId);
    }

    public void update(String id, String url, String secretId) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            int i = create.update(REPOSITORIES)
                    .set(REPOSITORIES.REPO_URL, url)
                    .set(REPOSITORIES.SECRET_ID, secretId)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .execute();

            if (i != 1) {
                log.error("update ['{}', '{}'] -> invalid number of rows updated: {}", id, url, i);
                throw new DataAccessException("Invalid number of rows updated: " + i);
            }
        });
        log.info("update ['{}', '{}', '{}'] -> done", id, url, secretId);
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);

            create.deleteFrom(REPOSITORIES)
                    .where(REPOSITORIES.REPO_ID.eq(id))
                    .execute();
        });
        log.info("delete ['{}'] -> done", id);
    }

    public List<RepositoryEntry> list(Field<?> sortField, boolean asc) {
        try (DSLContext create = DSL.using(cfg)) {
            SelectJoinStep<Record5<String, String, String, String, String>> query = selectRepositoryEntry(create);

            if (sortField != null) {
                query.orderBy(asc ? sortField.asc() : sortField.desc());
            }

            List<RepositoryEntry> result = query.fetch(RepositoryDao::toEntry);
            log.info("list [{}, {}] -> got {} result(s)", result.size());
            return result;
        }
    }

    public boolean exists(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            int cnt = create.fetchCount(create.selectFrom(REPOSITORIES)
                    .where(REPOSITORIES.REPO_NAME.eq(name)));

            return cnt > 0;
        }
    }

    private static SelectJoinStep<Record5<String, String, String, String, String>> selectRepositoryEntry(DSLContext create) {
        return create.select(REPOSITORIES.REPO_ID,
                REPOSITORIES.REPO_NAME,
                REPOSITORIES.REPO_URL,
                SECRETS.SECRET_ID,
                SECRETS.SECRET_NAME)
                .from(REPOSITORIES)
                .leftOuterJoin(SECRETS).on(SECRETS.SECRET_ID.eq(REPOSITORIES.SECRET_ID));
    }

    private static RepositoryEntry toEntry(Record5<String, String, String, String, String> r) {
        String secretId = r.get(SECRETS.SECRET_ID);
        IdName secret = secretId != null ? new IdName(secretId, r.get(SECRETS.SECRET_NAME)) : null;

        return new RepositoryEntry(r.get(REPOSITORIES.REPO_ID),
                r.get(REPOSITORIES.REPO_NAME),
                r.get(REPOSITORIES.REPO_URL),
                secret);
    }
}
