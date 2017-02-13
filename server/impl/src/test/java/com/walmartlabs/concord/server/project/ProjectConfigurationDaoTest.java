package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Test;

import java.util.Map;
import java.util.UUID;

import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ProjectConfigurationDaoTest extends AbstractDaoTest {

    @Test
    public void test() throws Exception {
        String projectId = UUID.randomUUID().toString();
        String projectName = "project#" + System.currentTimeMillis();
        ProjectDao projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao.insert(projectId, projectName, null);

        // ---

        ProjectAttachmentDao attachmentDao = new ProjectAttachmentDao(getConfiguration());
        ProjectConfigurationDao configurationDao = new ProjectConfigurationDao(getConfiguration(), attachmentDao);

        Long value1 = System.currentTimeMillis();
        Map<String, Object> data = singletonMap("a", singletonMap("b", singletonMap("c", value1)));
        configurationDao.insert(projectId, data);

        // ---

        Object value2 = configurationDao.getValue(projectId, "a", "b", "c");
        assertEquals(value1, value2);
    }
}
