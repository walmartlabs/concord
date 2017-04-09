package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.walmartlabs.concord.common.ConfigurationUtils;
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class ProjectConfigurationDao extends AbstractDao {

    public static final String PROJECT_CFG_ATTACHMENT_KEY = "_cfg";
    private static final Duration CACHE_TTL = Duration.of(1, ChronoUnit.MINUTES);

    private final ProjectAttachmentDao attachmentDao;
    private final ObjectMapper objectMapper;

    private final LoadingCache<String, Optional<Map<String, Object>>> cache;

    @Inject
    protected ProjectConfigurationDao(Configuration cfg, ProjectAttachmentDao attachmentDao) {
        super(cfg);
        this.attachmentDao = attachmentDao;
        this.objectMapper = new ObjectMapper();
        this.cache = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_TTL.getSeconds(), TimeUnit.SECONDS)
                .build(new Loader());
    }

    public void insert(String projectName, Map<String, Object> cfg) {
        tx(tx -> insert(tx, projectName, cfg));
    }

    public void insert(DSLContext tx, String projectName, Map<String, Object> cfg) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            objectMapper.writeValue(out, cfg);

            InputStream in = new ByteArrayInputStream(out.toByteArray());
            attachmentDao.insert(tx, projectName, PROJECT_CFG_ATTACHMENT_KEY, in);

            cache.put(projectName, Optional.of(cfg));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void delete(DSLContext tx, String projectName) {
        attachmentDao.delete(tx, projectName, PROJECT_CFG_ATTACHMENT_KEY);
        cache.invalidate(projectName);
    }

    public void update(String projectName, Map<String, Object> value) {
        tx(tx -> update(tx, projectName, value));
    }

    public void update(DSLContext tx, String projectName, Map<String, Object> cfg) {
        delete(tx, projectName);
        insert(tx, projectName, cfg);
    }

    public Map<String, Object> get(String projectName) {
        return cache.getUnchecked(projectName).orElse(null);
    }

    private Map<String, Object> _get(String projectName) {
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
        return ConfigurationUtils.get(cfg, path);
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

    @SuppressWarnings("unchecked")
    private class Loader extends CacheLoader<String, Optional<Map<String, Object>>> {

        @Override
        public Optional<Map<String, Object>> load(String key) throws Exception {
            Map<String, Object> m = _get(key);
            if (m == null) {
                return Optional.empty();
            }

            return Optional.of(m);
        }
    }
}
