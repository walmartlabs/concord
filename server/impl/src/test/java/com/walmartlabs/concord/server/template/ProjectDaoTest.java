package com.walmartlabs.concord.server.template;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.ProjectVisibility;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("requires a local DB instance")
public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private RepositoryDao repositoryDao;

    @BeforeEach
    public void setUp() {
        repositoryDao = new RepositoryDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE), getUuidGenerator());
        projectDao = new ProjectDao(getConfiguration(), new ConcordObjectMapper(TestObjectMapper.INSTANCE), getUuidGenerator());
    }

    @Test
    public void testInsertDelete() {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        Map<String, Object> cfg = ImmutableMap.of("a", "a-v");
        String projectName = "project#" + System.currentTimeMillis();
        UUID projectId = projectDao.insert(orgId, projectName, "test", null, cfg, null, null, new byte[0], null, null, null);

        // ---
        Map<String, Object> actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(cfg, actualCfg);

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        String repoUrl = "n/a";
        repositoryDao.insert(projectId, repoName, repoUrl, null, null, null, null, false, null, false);

        // ---
        Map<String, Object> newCfg1 = ImmutableMap.of("a1", "a1-v");
        tx(tx -> projectDao.updateCfg(tx, projectId, newCfg1));

        actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(newCfg1, actualCfg);

        // ---
        Map<String, Object> newCfg2 = ImmutableMap.of("a2", "a2-v");
        tx(tx -> projectDao.update(tx, orgId, projectId, ProjectVisibility.PRIVATE, projectName, "new-description", newCfg2, null, null, null, null, null));

        actualCfg = projectDao.getConfiguration(projectId);
        assertEquals(newCfg2, actualCfg);

        // ---
        String v = (String) projectDao.getConfigurationValue(projectId, "a2");
        assertEquals("a2-v", v);

        // ---
        projectDao.delete(projectId);

        // ---

        assertNull(projectDao.getId(orgId, projectName));
        assertNull(repositoryDao.getId(projectId, repoName));
    }

    @Test
    public void testList() {
        UUID orgId = OrganizationManager.DEFAULT_ORG_ID;

        assertEquals(0, projectDao.list(null, null, PROJECTS.PROJECT_NAME, true, 0, -1, null).size());

        // ---

        String aName = "aProject#" + System.currentTimeMillis();
        String bName = "bProject#" + System.currentTimeMillis();
        String cName = "cProject#" + System.currentTimeMillis();

        projectDao.insert(orgId, aName, "test", null, null, null, null, new byte[0], null, null, null);
        projectDao.insert(orgId, bName, "test", null, null, null, null, new byte[0], null, null, null);
        projectDao.insert(orgId, cName, "test", null, null, null, null, new byte[0], null, null, null);

        // ---

        List<ProjectEntry> l = projectDao.list(null, null, PROJECTS.PROJECT_NAME, false, 0, -1, null);
        assertEquals(3, l.size());

        ProjectEntry a = l.get(2);
        assertEquals(aName, a.getName());

        ProjectEntry b = l.get(1);
        assertEquals(bName, b.getName());

        ProjectEntry c = l.get(0);
        assertEquals(cName, c.getName());

        // ---

        List<ProjectEntry> l2 = projectDao.list(null, null, PROJECTS.PROJECT_NAME, false, 1, -1, null);
        assertEquals(2, l2.size());

        ProjectEntry a2 = l2.get(1);
        assertEquals(aName, a2.getName());

        ProjectEntry b2 = l2.get(0);
        assertEquals(bName, b2.getName());

        // ---

        List<ProjectEntry> l3 = projectDao.list(null, null, PROJECTS.PROJECT_NAME, false, 0, -1, "cProject");
        assertEquals(1, l3.size());

        ProjectEntry c3 = l3.get(0);
        assertEquals(cName, c3.getName());

        // ---

        List<ProjectEntry> l4 = projectDao.list(null, null, PROJECTS.PROJECT_NAME, false, 0, 1, null);
        assertEquals(1, l4.size());

        ProjectEntry c4 = l4.get(0);
        assertEquals(cName, c4.getName());
    }
}
