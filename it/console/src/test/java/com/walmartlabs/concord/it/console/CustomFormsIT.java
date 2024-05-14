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
import org.openqa.selenium.WebElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.common.ServerClient.*;
import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The Console must be running in Docker, i.e. API redirects must be correctly working.
 */
@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class CustomFormsIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @SuppressWarnings("unchecked")
    @Test
    public void test() throws Exception {
        ApiClient client = serverRule.getClient();

        // ---

        String gitUrl = ITUtils.createGitRepo(CustomFormsIT.class, "customForm");

        // ---

        String orgName = "org_" + ITUtils.randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(client);
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + ITUtils.randomString();
        String repoName = "test";

        ProjectsApi projectsApi = new ProjectsApi(client);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));

        // ---

        consoleRule.login(Concord.ADMIN_API_KEY);

        // ---

        String testValue = "test_" + ITUtils.randomString();

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        input.put("arguments.testValue", testValue);

        StartProcessResponse spr = serverRule.start(input);
        assertNotNull(spr.getInstanceId());

        ProcessEntry pir = waitForStatus(client, spr.getInstanceId(), ProcessEntry.StatusEnum.SUSPENDED);

        String url = "/#/process/" + spr.getInstanceId() + "/wizard";
        consoleRule.navigateToRelative(url);

        //  -- validate form rules and submit it

        By selector = By.id("testValue");
        WebElement element = consoleRule.waitFor(selector);
        assertEquals(testValue, element.getText());

        Map<String, Object> formFields = (Map<String, Object>) consoleRule.executeJavaScript("return data.definitions");
        Map<String, Object> fieldX = (Map<String, Object>) formFields.get("x");
        List<Object> allowedValues = (List<Object>) fieldX.get("allow");
        assertEquals(2, allowedValues.size(), "Expression object should have added two allowed values");

        WebElement submitButton = consoleRule.waitFor(By.id("submitButton"));
        submitButton.click();

        //  -- validate log output

        pir = waitForCompletion(client, pir.getInstanceId());

        byte[] ab = serverRule.getLog(pir.getInstanceId());
        assertLog(".*uploaded contents: \\{hello=world\\}.*", ab);
    }
}
