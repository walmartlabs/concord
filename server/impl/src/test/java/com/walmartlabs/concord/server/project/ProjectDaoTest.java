package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.repository.RepositoryDao;
import com.walmartlabs.concord.server.template.TemplateDao;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.public_.tables.Projects.PROJECTS;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private TemplateDao templateDao;
    private RepositoryDao repositoryDao;

    @Before
    public void setUp() throws Exception {
        repositoryDao = new RepositoryDao(getConfiguration(), mock(UserPermissionCleaner.class));
        projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        templateDao = new TemplateDao(getConfiguration(), mock(UserPermissionCleaner.class));
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
        repositoryDao.insert(projectId, repoId, repoName, repoUrl, null, null);

        // ---

        projectDao.delete(projectId);

        // ---

        assertNull(projectDao.getName(projectId));
        assertNotNull(templateDao.getId(templateName));
        assertNull(repositoryDao.getByName(repoName));
    }

    @Test
    public void testList() throws Exception {
        String aTemplateId = UUID.randomUUID().toString();
        String aTemplateName = "aTemplate#" + System.currentTimeMillis();
        String bTemplateId = UUID.randomUUID().toString();
        String bTemplateName = "bTemplate#" + System.currentTimeMillis();

        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(aTemplateId, aTemplateName, templateData);
        templateDao.insert(bTemplateId, bTemplateName, templateData);

        // ---

        assertEquals(0, projectDao.list(PROJECTS.PROJECT_NAME, true).size());

        // ---

        String aId = UUID.randomUUID().toString();
        String aName = "aProject#" + System.currentTimeMillis();
        String[] aTemplateIds = {aTemplateId, bTemplateId};
        String[] aTemplateNames = {aTemplateName, bTemplateName};

        String bId = UUID.randomUUID().toString();
        String bName = "bProject#" + System.currentTimeMillis();
        String[] bTemplateIds = {aTemplateId, bTemplateId};
        String[] bTemplateNames = {aTemplateName, bTemplateName};

        projectDao.insert(aId, aName, aTemplateIds);
        projectDao.insert(bId, bName, bTemplateIds);

        // ---

        List<ProjectEntry> l = projectDao.list(PROJECTS.PROJECT_NAME, false);
        assertEquals(2, l.size());

        ProjectEntry a = l.get(1);
        assertEquals(aId, a.getId());
        assertArrayEquals(aTemplateNames, a.getTemplates());

        ProjectEntry b = l.get(0);
        assertEquals(bId, b.getId());
        assertArrayEquals(bTemplateNames, b.getTemplates());
    }
}
