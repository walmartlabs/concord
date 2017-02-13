package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.InputStream;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.public_.tables.ProjectAttachments.PROJECT_ATTACHMENTS;

@Named
public class ProjectAttachmentDao extends AbstractDao {

    @Inject
    protected ProjectAttachmentDao(Configuration cfg) {
        super(cfg);
    }

    public void insert(DSLContext tx, String projectId, String name, InputStream data) {

        Function<DSLContext, String> sqlFn = ctx -> ctx.insertInto(PROJECT_ATTACHMENTS)
                .columns(PROJECT_ATTACHMENTS.PROJECT_ID, PROJECT_ATTACHMENTS.ATTACHMENT_NAME, PROJECT_ATTACHMENTS.ATTACHMENT_DATA)
                .values((String) null, null, null)
                .getSQL();

        executeUpdate(tx, sqlFn, ps -> {
            ps.setString(1, projectId);
            ps.setString(2, name);
            ps.setBinaryStream(3, data);
        });
    }

    public void delete(DSLContext tx, String projectId, String name) {
        tx.deleteFrom(PROJECT_ATTACHMENTS)
                .where(PROJECT_ATTACHMENTS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_ATTACHMENTS.ATTACHMENT_NAME.eq(name)))
                .execute();
    }

    public InputStream get(String projectId, String name) {
        Function<DSLContext, String> sql = create -> create.select(PROJECT_ATTACHMENTS.ATTACHMENT_DATA)
                .from(PROJECT_ATTACHMENTS)
                .where(PROJECT_ATTACHMENTS.PROJECT_ID.eq(projectId)
                        .and(PROJECT_ATTACHMENTS.ATTACHMENT_NAME.eq(name)))
                .getSQL();

        return getData(sql, ps -> {
            ps.setString(1, projectId);
            ps.setString(2, name);
        }, 1);
    }
}
