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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.ITUtils;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * The Console must be running in Docker, i.e. API redirects must be correctly working.
 */
public class CustomFormsIT {

    @Rule
    public ConcordServerRule serverRule = new ConcordServerRule();

    @Rule
    public ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @SuppressWarnings("unchecked")
    @Test(timeout = 60000)
    public void test() throws Exception {
        ApiClient client = serverRule.getClient();

        // ---

        String gitUrl = ITUtils.createGitRepo(CustomFormsIT.class, "customForm");

        // ---

        String orgName = "org_" + ITUtils.randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(client);
        organizationsApi.createOrUpdate(new OrganizationEntry().setName(orgName));

        String projectName = "project_" + ITUtils.randomString();
        String repoName = "test";

        ProjectsApi projectsApi = new ProjectsApi(client);
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(gitUrl))));

        // ---

        consoleRule.login(Concord.ADMIN_API_KEY);

        // ---

        String testValue = "test_" + ITUtils.randomString();

        String url = "/api/v1/org/" + orgName + "/project/" + projectName + "/repo/" + repoName + "/start/default?testValue=" + testValue;
        consoleRule.navigateToRelative(url);

        By selector = By.id("testValue");
        WebElement element = consoleRule.waitFor(selector);
        assertEquals(testValue, element.getText());

        Map<String,Object> formFields = (Map<String,Object>) consoleRule.executeJavaScript("return data.definitions");
        Map<String,Object> fieldX = (Map<String,Object>) formFields.get("x");
        List<Object> allowedValues = (List<Object>)fieldX.get("allow");
        assertEquals("Expression object should have added two allowed values",2, allowedValues.size());
    }
}
