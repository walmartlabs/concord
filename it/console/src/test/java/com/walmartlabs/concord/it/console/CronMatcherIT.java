package com.walmartlabs.concord.it.console;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.OrganizationEntry;
import com.walmartlabs.concord.client2.OrganizationsApi;
import com.walmartlabs.concord.client2.ProjectEntry;
import com.walmartlabs.concord.client2.ProjectsApi;
import com.walmartlabs.concord.client2.RepositoryEntry;
import com.walmartlabs.concord.it.common.ITUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class CronMatcherIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testCronTriggerNextRunRendering() throws Exception {
        var client = serverRule.getClient();

        var gitUrl = ITUtils.createGitRepo(CronMatcherIT.class, "cronTrigger");
        var orgName = "org_" + ITUtils.randomString();
        var projectName = "project_" + ITUtils.randomString();
        var repoName = "repo_" + ITUtils.randomString();

        createOrgAndProject(client, orgName, projectName, repoName, gitUrl);

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/project/" + projectName + "/repository/" + repoName + "/triggers");
        consoleRule.waitForLoad();

        var conditions = consoleRule.waitFor(By.xpath("//pre[contains(., 'Expression:') and contains(., '0 10 * * *')]"));
        var text = conditions.getText();

        assertTrue(text.contains("Next run:"));
        assertTrue(text.contains("Timezone: America/Toronto"));
        assertFalse(text.contains("Unavailable"));
        assertFalse(text.contains("Bad date value"));
    }

    private void createOrgAndProject(
            ApiClient client,
            String orgName,
            String projectName,
            String repoName,
            String gitUrl
    ) throws Exception {
        var organizationsApi = new OrganizationsApi(client);
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        var projectsApi = new ProjectsApi(client);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));
    }
}
