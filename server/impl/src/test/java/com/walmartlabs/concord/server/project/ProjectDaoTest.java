package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.repository.RepositoryDao;
import com.walmartlabs.concord.server.template.TemplateDao;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.UUID;

import static org.junit.Assert.*;

public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private TemplateDao templateDao;
    private RepositoryDao repositoryDao;

    @Before
    public void setUp() throws Exception {
        repositoryDao = new RepositoryDao(getConfiguration());
        projectDao = new ProjectDao(getConfiguration());
        templateDao = new TemplateDao(getConfiguration());
    }

    @Test
    public void testInsertWithTemplates() throws Exception {
        String templateId = UUID.randomUUID().toString();
        String templateName = "template#" + System.currentTimeMillis();
        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(templateId, templateName, templateData);

        // ---

        String projectId = UUID.randomUUID().toString();
        String projectName = "project#" + System.currentTimeMillis();
        String[] projectTemplateIds = {templateId};

        projectDao.insert(projectId, projectName, projectTemplateIds);

        // ---

        Collection<String> ids = templateDao.getProjectTemplateIds(projectId);
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertEquals(templateId, ids.iterator().next());
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

        projectDao.insert(projectId, projectName, projectTemplateIds);

        // ---

        String repoId = UUID.randomUUID().toString();
        String repoName = "repo#" + System.currentTimeMillis();
        String repoUrl = "n/a";
        repositoryDao.insert(projectId, repoId, repoName, repoUrl);

        // ---

        projectDao.delete(projectId);

        // ---

        assertNull(projectDao.getName(projectId));
        assertNotNull(templateDao.getId(templateName));
        assertNull(repositoryDao.findUrl(projectId, repoName));
    }
}
