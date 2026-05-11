package com.walmartlabs.concord.it.console;

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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.it.common.ITUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class RepositoryRunIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testRunRepositoryFromUi() throws Exception {
        var client = serverRule.getClient();

        var gitUrl = ITUtils.createGitRepo(RepositoryRunIT.class, "repositoryRun");
        var orgName = "org_" + ITUtils.randomString();
        var projectName = "project_" + ITUtils.randomString();
        var repoName = "repo_" + ITUtils.randomString();

        createOrgAndProject(client, orgName, projectName, repoName, gitUrl);

        consoleRule.login(Concord.ADMIN_API_KEY);
        var repositoriesUrl = "/#/org/" + orgName + "/project/" + projectName + "/repository";
        var runButtonSelector = By.cssSelector("[data-testid='repository-run-button-" + repoName + "']");
        waitForRepositoryPage(consoleRule, repositoriesUrl, runButtonSelector);

        var runButton = consoleRule.waitFor(runButtonSelector);
        runButton.click();

        Thread.sleep(500);
        var startButton = consoleRule.waitFor(By.cssSelector(".ui.modal.active .actions .blue.button"));
        startButton.click();

        var openProcessPageButton = consoleRule.waitFor(By.xpath("//button[contains(normalize-space(.), 'Open the process page')]"));
        assertNotNull(openProcessPageButton);
        openProcessPageButton.click();
        Thread.sleep(1000);
        consoleRule.waitForLoad();

        var processV2Api = new ProcessV2Api(client);
        var processes = processV2Api.listProcesses(ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .repoName(repoName)
                .build());

        assertFalse(processes.isEmpty());

        var process = waitForCompletion(client, processes.get(0).getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, process.getStatus());

        var log = serverRule.getLog(process.getInstanceId());
        assertLog(".*Hello!.*", log);
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

    private void waitForRepositoryPage(ConcordConsoleRule consoleRule, String url, By runButtonSelector) throws Exception {
        for (var attempt = 0; attempt < 10; attempt++) {
            consoleRule.navigateToRelative(url);
            consoleRule.waitForLoad();

            if (!consoleRule.getDriver().findElements(runButtonSelector).isEmpty()) {
                return;
            }

            Thread.sleep(1000);
        }
    }
}
