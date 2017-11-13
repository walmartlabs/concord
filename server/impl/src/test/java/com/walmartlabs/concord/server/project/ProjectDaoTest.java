package com.walmartlabs.concord.server.project;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.team.TeamManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Ignore("requires a local DB instance")
public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private RepositoryDao repositoryDao;

    @Before
    public void setUp() throws Exception {
        repositoryDao = new RepositoryDao(getConfiguration());
        projectDao = new ProjectDao(getConfiguration());
    }

    @Test
    public void testInsertDelete() throws Exception {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        Map<String, Object> cfg = ImmutableMap.of("a", "a-v");
        String projectName = "project#" + System.currentTimeMillis();
        UUID projectId = projectDao.insert(teamId, projectName, "test", cfg, null);

        // ---
        Map<String, Object> actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(cfg, actualCfg);

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        String repoUrl = "n/a";
        repositoryDao.insert(projectId, repoName, repoUrl, null, null, null, null);

        // ---
        Map<String, Object> newCfg1 = ImmutableMap.of("a1", "a1-v");
        tx(tx -> projectDao.update(tx, projectId, newCfg1));

        actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(newCfg1, actualCfg);

        // ---
        Map<String, Object> newCfg2 = ImmutableMap.of("a2", "a2-v");
        tx(tx -> projectDao.update(tx, teamId, projectId, projectName, "new-description", newCfg2));

        actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(newCfg2, actualCfg);

        // ---
        String v = (String) projectDao.getConfigurationValue(projectId, "a2");
        assertEquals("a2-v", v);

        // ---
        projectDao.delete(projectId);

        // ---

        assertNull(projectDao.getId(teamId, projectName));
        assertNull(repositoryDao.getId(projectId, repoName));
    }

    @Test
    public void testList() throws Exception {
        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        assertEquals(0, projectDao.list(null, PROJECTS.PROJECT_NAME, true).size());

        // ---

        String aName = "aProject#" + System.currentTimeMillis();
        String bName = "bProject#" + System.currentTimeMillis();

        projectDao.insert(teamId, aName, "test", null, null);
        projectDao.insert(teamId, bName, "test", null, null);

        // ---

        List<ProjectEntry> l = projectDao.list(null, PROJECTS.PROJECT_NAME, false);
        assertEquals(2, l.size());

        ProjectEntry a = l.get(1);
        assertEquals(aName, a.getName());

        ProjectEntry b = l.get(0);
        assertEquals(bName, b.getName());
    }
}
