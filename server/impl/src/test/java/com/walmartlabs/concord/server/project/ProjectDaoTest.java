package com.walmartlabs.concord.server.project;

import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.template.TemplateDao;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private TemplateDao templateDao;
    private RepositoryDao repositoryDao;

    @Before
    public void setUp() throws Exception {
        repositoryDao = new RepositoryDao(getConfiguration());
        projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
        templateDao = new TemplateDao(getConfiguration(), mock(UserPermissionCleaner.class));
    }

    @Test
    public void testInsertWithTemplates() throws Exception {
        String templateName = "template#" + System.currentTimeMillis();
        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(templateName, templateData);

        // ---

        String projectName = "project#" + System.currentTimeMillis();
        String[] projectTemplates = {templateName};

        projectDao.insert(projectName, "test", Arrays.asList(projectTemplates));

        // ---

        Collection<String> ids = templateDao.getProjectTemplates(projectName);
        assertNotNull(ids);
        assertEquals(1, ids.size());
        assertEquals(templateName, ids.iterator().next());
    }

    @Test
    public void testInsertDelete() throws Exception {
        String templateName = "template#" + System.currentTimeMillis();
        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(templateName, templateData);

        // ---

        String projectName = "project#" + System.currentTimeMillis();
        String[] projectTemplates = {templateName};

        projectDao.insert(projectName, "test", Arrays.asList(projectTemplates));

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        String repoUrl = "n/a";
        repositoryDao.insert(projectName, repoName, repoUrl, null, null, null);

        // ---

        projectDao.delete(projectName);

        // ---

        assertFalse(projectDao.exists(projectName));
        assertTrue(templateDao.exists(templateName));
        assertFalse(repositoryDao.exists(projectName, repoName));
    }

    @Test
    public void testList() throws Exception {
        String aTemplateName = "aTemplate#" + System.currentTimeMillis();
        String bTemplateName = "bTemplate#" + System.currentTimeMillis();

        InputStream templateData = new ByteArrayInputStream(new byte[]{1, 2, 3});

        templateDao.insert(aTemplateName, templateData);
        templateDao.insert(bTemplateName, templateData);

        // ---

        assertEquals(0, projectDao.list(PROJECTS.PROJECT_NAME, true).size());

        // ---

        String aName = "aProject#" + System.currentTimeMillis();
        String[] aTemplates = {aTemplateName, bTemplateName};
        Set<String> aTemplateNames = new HashSet<>();
        aTemplateNames.add(aTemplateName);
        aTemplateNames.add(bTemplateName);

        String bName = "bProject#" + System.currentTimeMillis();
        String[] bTemplates = {aTemplateName, bTemplateName};
        Set<String> bTemplateNames = new HashSet<>();
        bTemplateNames.add(aTemplateName);
        bTemplateNames.add(bTemplateName);

        projectDao.insert(aName, "test", Arrays.asList(aTemplates));
        projectDao.insert(bName, "test", Arrays.asList(bTemplates));

        // ---

        List<ProjectEntry> l = projectDao.list(PROJECTS.PROJECT_NAME, false);
        assertEquals(2, l.size());

        ProjectEntry a = l.get(1);
        assertEquals(aName, a.getName());
        assertSetEquals(aTemplateNames, a.getTemplates());

        ProjectEntry b = l.get(0);
        assertEquals(bName, b.getName());
        assertSetEquals(bTemplateNames, b.getTemplates());
    }

    private static void assertSetEquals(Set<?> a, Set<?> b) {
        assertTrue(a.containsAll(b) && b.containsAll(a));
    }
}
