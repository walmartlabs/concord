package com.walmartlabs.concord.server.template;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.db.AbstractDao;
import com.walmartlabs.concord.common.db.ResultSetInputStream;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.JDBCUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectTemplates.PROJECT_TEMPLATES;
import static com.walmartlabs.concord.server.jooq.public_.tables.Templates.TEMPLATES;

@Named
public class TemplateDao extends AbstractDao {

    private static final Logger log = LoggerFactory.getLogger(TemplateDao.class);

    @Inject
    public TemplateDao(Configuration cfg) {
        super(cfg);
    }

    public Collection<String> getProjectTemplateIds(String projectId) {
        try (DSLContext create = DSL.using(cfg)) {
            Collection<String> r = create.select(PROJECT_TEMPLATES.TEMPLATE_ID)
                    .from(PROJECT_TEMPLATES)
                    .where(PROJECT_TEMPLATES.PROJECT_ID.eq(projectId))
                    .fetch(PROJECT_TEMPLATES.TEMPLATE_ID);

            log.info("getProjectTemplateIds ['{}'] -> found {} template(s)", projectId, r.size());
            return r;
        }
    }

    public InputStream get(String id) {
        String sql;
        try (DSLContext create = DSL.using(cfg)) {
            sql = create.select(TEMPLATES.TEMPLATE_DATA)
                    .from(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_ID.eq(id))
                    .getSQL();
        }

        Connection conn = cfg.connectionProvider().acquire();

        PreparedStatement ps = null;
        try {
            ps = conn.prepareStatement(sql);
            ps.setString(1, id);

            InputStream in = ResultSetInputStream.open(conn, ps, 1);
            if (in == null) {
                JDBCUtils.safeClose(ps);
                JDBCUtils.safeClose(conn);
                return null;
            }
            return in;
        } catch (SQLException e) {
            JDBCUtils.safeClose(ps);
            JDBCUtils.safeClose(conn);
            throw new DataAccessException("Error while opening a stream", e);
        }
    }

    public void insert(String id, String name, InputStream data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            String sql = create.insertInto(TEMPLATES)
                    .columns(TEMPLATES.TEMPLATE_ID, TEMPLATES.TEMPLATE_NAME, TEMPLATES.TEMPLATE_DATA)
                    .values((String) null, null, null)
                    .getSQL();

            create.connection(conn -> {
                try (PreparedStatement ps = conn.prepareCall(sql)) {
                    ps.setString(1, id);
                    ps.setString(2, name);
                    ps.setBinaryStream(3, data);
                    ps.executeUpdate();
                }
            });
        });
        log.info("insert ['{}', '{}'] -> done", id, name);
    }

    public void update(String id, InputStream data) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            String sql = create.update(TEMPLATES)
                    .set(TEMPLATES.TEMPLATE_DATA, (byte[]) null)
                    .where(TEMPLATES.TEMPLATE_ID.eq(id))
                    .getSQL();

            create.connection(conn -> {
                try (PreparedStatement ps = conn.prepareCall(sql)) {
                    ps.setBinaryStream(1, data);
                    ps.setString(2, id);
                    ps.executeUpdate();
                }
            });
        });
        log.info("update ['{}'] -> done", id);
    }

    public void delete(String id) {
        transaction(cfg -> {
            DSLContext create = DSL.using(cfg);
            create.deleteFrom(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_ID.eq(id))
                    .execute();
        });
        log.info("delete ['{}'] -> done", id);
    }

    public String getId(String name) {
        try (DSLContext create = DSL.using(cfg)) {
            return create.select(TEMPLATES.TEMPLATE_ID)
                    .from(TEMPLATES)
                    .where(TEMPLATES.TEMPLATE_NAME.eq(name))
                    .fetchOne(TEMPLATES.TEMPLATE_ID);
        }
    }
}
