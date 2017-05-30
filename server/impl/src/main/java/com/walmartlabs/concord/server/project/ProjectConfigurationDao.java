package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;

@Named
@Singleton
public class ProjectConfigurationDao extends AbstractDao {

    private final ObjectMapper objectMapper;

    @Inject
    public ProjectConfigurationDao(Configuration cfg) {
        super(cfg);
        this.objectMapper = new ObjectMapper();
    }

    public void insert(String projectName, Map<String, Object> cfg) {
        tx(tx -> insert(tx, projectName, cfg));
    }

    public void insert(DSLContext tx, String projectName, Map<String, Object> cfg) {
        update(tx, projectName, cfg);
    }

    public void update(String projectName, Map<String, Object> value) {
        tx(tx -> update(tx, projectName, value));
    }

    public void update(DSLContext tx, String projectName, Map<String, Object> cfg) {
        byte[] ab;
        try {
            ab = objectMapper.writeValueAsBytes(cfg);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        tx.update(PROJECTS)
                .set(PROJECTS.PROJECT_CFG, ab)
                .where(PROJECTS.PROJECT_NAME.eq(projectName))
                .execute();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String projectName) {
        try (DSLContext tx = DSL.using(cfg)) {
            byte[] ab = tx.select(PROJECTS.PROJECT_CFG)
                    .from(PROJECTS)
                    .where(PROJECTS.PROJECT_NAME.eq(projectName))
                    .fetchOne(PROJECTS.PROJECT_CFG);

            if (ab == null) {
                return null;
            }

            return objectMapper.readValue(ab, Map.class);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public Object getValue(String projectName, String... path) {
        Map<String, Object> cfg = get(projectName);
        return ConfigurationUtils.get(cfg, path);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getList(String projectName, String... path) {
        Object v = getValue(projectName, path);
        if (v == null) {
            return null;
        }
        if (!(v instanceof List)) {
            throw new IllegalArgumentException("Invalid data type, expected list, got: " + v.getClass());
        }
        return (List<Map<String, Object>>) v;
    }
}
