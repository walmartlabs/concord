package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.project.ProjectDao;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class TemplateDaoTest extends AbstractDaoTest {

    private TemplateDao templateDao;
    private ProjectDao projectDao;

    @Before
    public void setUp() throws Exception {
        templateDao = new TemplateDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
    }

    @Test
    public void testInsert() throws Exception {
        String id = UUID.randomUUID().toString();
        String name = "test#" + System.currentTimeMillis();
        InputStream data = new ByteArrayInputStream(new byte[]{1, 2, 3});

        // ---

        templateDao.insert(id, name, data);

        // ---

        try (InputStream in = templateDao.get(id)) {
            assertNotNull(in);
            assertEquals(1, in.read());
            assertEquals(2, in.read());
            assertEquals(3, in.read());
        }
    }

    @Test
    public void testInsertDelete() throws Exception {
        String templateId = UUID.randomUUID().toString();
        String templateName = "template#" + System.currentTimeMillis();
        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(templateId, templateName, templateData);

        // ---

        String projectId = UUID.randomUUID().toString();
        String projectName = "project#" + System.currentTimeMillis();
        String[] projectTemplateIds = {templateId};

        projectDao.insert(projectId, projectName, Arrays.asList(projectTemplateIds));

        // ---

        templateDao.delete(templateId);

        assertNull(templateDao.get(templateId));
        assertEquals(projectId, projectDao.getId(projectName));
        Collection<String> ids = templateDao.getProjectTemplateIds(projectId);
        assertTrue(ids.isEmpty());
    }
}
