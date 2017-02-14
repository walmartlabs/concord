package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Named
@Singleton
public class ProjectConfigurationDao extends AbstractDao {

    public static String PROJECT_CFG_ATTACHMENT_KEY = "_cfg";

    private final ProjectAttachmentDao attachmentDao;
    private final ObjectMapper objectMapper;

    @Inject
    protected ProjectConfigurationDao(Configuration cfg, ProjectAttachmentDao attachmentDao) {
        super(cfg);
        this.attachmentDao = attachmentDao;
        this.objectMapper = new ObjectMapper();
    }

    public void insert(String projectName, Map<String, Object> cfg) {
        tx(tx -> insert(tx, projectName, cfg));
    }

    public void insert(DSLContext tx, String projectName, Map<String, Object> cfg) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            objectMapper.writeValue(out, cfg);

            InputStream in = new ByteArrayInputStream(out.toByteArray());
            attachmentDao.insert(tx, projectName, PROJECT_CFG_ATTACHMENT_KEY, in);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void delete(DSLContext tx, String projectName) {
        attachmentDao.delete(tx, projectName, PROJECT_CFG_ATTACHMENT_KEY);
    }

    public Map<String, Object> get(String projectName) {
        try (InputStream in = attachmentDao.get(projectName, PROJECT_CFG_ATTACHMENT_KEY)) {
            if (in == null) {
                return null;
            }
            return objectMapper.readValue(in, Map.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public Object getValue(String projectName, String... path) {
        Map<String, Object> cfg = get(projectName);
        return get(cfg, path);
    }

    public List<Map<String, Object>> getList(String projectName, String... path) {
        Object v = getValue(projectName, path);
        if (v == null) {
            return null;
        }
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Invalid data type, expected list, got: " + v.getClass());
        }
        return (List) v;
    }

    private Object get(Map<String, Object> m, String... path) {
        if (m == null) {
            return null;
        }

        for (int i = 0; i < path.length - 1; i++) {
            Object v = m.get(path[i]);
            if (v == null) {
                return null;
            }

            if (!(v instanceof Map)) {
                throw new IllegalArgumentException("Invalid data type, expected map, got: " + v.getClass());
            }

            m = (Map<String, Object>) v;
        }

        return m.get(path[path.length - 1]);
    }
}
