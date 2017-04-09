package com.walmartlabs.concord.server.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

public class ProjectConfigurationDaoTest extends AbstractDaoTest {

    @Test
    public void test() throws Exception {
        String projectName = "project#" + System.currentTimeMillis();
        ProjectDao projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao.insert(projectName, null);

        // ---

        ProjectAttachmentDao attachmentDao = new ProjectAttachmentDao(getConfiguration());
        ProjectConfigurationDao configurationDao = new ProjectConfigurationDao(getConfiguration(), attachmentDao);

        Long value1 = System.currentTimeMillis();
        Map<String, Object> data = singletonMap("a", singletonMap("b", singletonMap("c", value1)));
        configurationDao.insert(projectName, data);

        // ---

        Object value2 = configurationDao.getValue(projectName, "a", "b", "c");
        assertEquals(value1, value2);
    }

    @Test
    public void testCache() throws Exception {
        String projectName = "project#" + System.currentTimeMillis();
        ProjectDao projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao.insert(projectName, null);

        // ---

        String k = "k#" + System.currentTimeMillis();
        Map<String, Object> data = Collections.singletonMap(k, "v#" + System.currentTimeMillis());

        // ---

        ProjectAttachmentDao attachmentDao = spy(new ProjectAttachmentDao(getConfiguration()));
        ProjectConfigurationDao configurationDao = new ProjectConfigurationDao(getConfiguration(), attachmentDao);
        configurationDao.insert(projectName, data);

        // ---

        for (int i = 0; i < 10; i++) {
            Object v = configurationDao.getValue(projectName, k);
            assertNotNull(v);
        }

        // ---

        verify(attachmentDao, times(0)).get(eq(projectName), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testUpdate() throws Exception {
        String projectName = "project#" + System.currentTimeMillis();
        ProjectDao projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao.insert(projectName, null);

        // ---

        ObjectMapper om = new ObjectMapper();

        String json1 = "{ \"smtp\": { \"host\": \"localhost\", \"port\": 25 }, \"ssl\": true }";
        Map<String, Object> cfg1 = om.readValue(json1, Map.class);

        // ---

        ProjectAttachmentDao attachmentDao = spy(new ProjectAttachmentDao(getConfiguration()));
        ProjectConfigurationDao configurationDao = new ProjectConfigurationDao(getConfiguration(), attachmentDao);
        configurationDao.insert(projectName, cfg1);
        assertEquals("localhost", configurationDao.getValue(projectName, "smtp", "host"));

        // ---

        String json2 = "{ \"host\": \"127.0.0.1\" }";
        Map<String, Object> partial1 = om.readValue(json2, Map.class);

        ConfigurationUtils.merge(cfg1, partial1, "smtp");

        configurationDao.update(projectName, cfg1);
        assertEquals("127.0.0.1", configurationDao.getValue(projectName, "smtp", "host"));

        // ---

        String json3 = "{ \"smtp\": { \"host\": \"10.11.12.13\" } }";
        Map<String, Object> partial2 = om.readValue(json3, Map.class);

        ConfigurationUtils.merge(cfg1, partial2);

        configurationDao.update(projectName, cfg1);
        assertEquals("10.11.12.13", configurationDao.getValue(projectName, "smtp", "host"));
        assertEquals(true, configurationDao.getValue(projectName, "ssl"));
        assertNull(configurationDao.getValue(projectName, "smtp", "port"));
    }
}
