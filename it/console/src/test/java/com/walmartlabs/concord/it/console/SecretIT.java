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
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.Select;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class SecretIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testSecretSettingsVisibility() throws Exception {
        var client = serverRule.getClient();

        var orgName = "org_" + ITUtils.randomString();
        var secretName = "secret_" + ITUtils.randomString();

        createOrg(client, orgName);
        createUsernamePasswordSecret(client, orgName, secretName, SecretEntryV2.VisibilityEnum.PUBLIC);

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/secret/" + secretName + "/settings");
        consoleRule.waitForLoad();

        var visibilitySelect = consoleRule.waitFor(By.cssSelector("[data-testid='secret-visibility-select']"));
        new Select(visibilitySelect).selectByVisibleText("Private");
        Thread.sleep(300);

        var changeButton = consoleRule.waitFor(By.xpath("//button[normalize-space()='Change' and not(@disabled)]"));
        changeButton.click();

        Thread.sleep(500);
        var confirmButton = consoleRule.waitFor(By.cssSelector(".ui.modal.active .actions .ui.primary.button"));
        confirmButton.click();
        Thread.sleep(1500);

        var secret = new SecretsV2Api(client).getSecret(orgName, secretName);
        assertEquals(SecretEntryV2.VisibilityEnum.PRIVATE, secret.getVisibility());
    }

    @Test
    public void testSecretTeamAccess() throws Exception {
        var client = serverRule.getClient();

        var orgName = "org_" + ITUtils.randomString();
        var secretName = "secret_" + ITUtils.randomString();
        var teamName = "team_" + ITUtils.randomString();

        createOrg(client, orgName);
        createUsernamePasswordSecret(client, orgName, secretName, SecretEntryV2.VisibilityEnum.PUBLIC);
        createTeamWithUser(client, orgName, teamName);

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/secret/" + secretName + "/access");
        consoleRule.waitForLoad();

        var editButton = consoleRule.waitFor(By.cssSelector("[data-testid='team-access-edit-btn']"));
        editButton.click();
        Thread.sleep(500);

        var teamDropdown = consoleRule.waitFor(By.cssSelector("[data-testid='team-access-add-dropdown'] input"));
        teamDropdown.click();
        new Actions(consoleRule.getDriver())
                .sendKeys(teamName)
                .pause(500)
                .sendKeys(Keys.ENTER)
                .perform();
        Thread.sleep(500);

        var teamRow = consoleRule.waitFor(By.cssSelector("[data-testid='team-access-row-" + teamName + "']"));
        assertNotNull(teamRow);

        var saveButton = consoleRule.waitFor(By.cssSelector("[data-testid='team-access-save-btn']:not([disabled])"));
        saveButton.click();
        Thread.sleep(1500);

        var accessList = new SecretsApi(client).getSecretAccessLevel(orgName, secretName);
        assertEquals(1, accessList.size());
        assertEquals(teamName, accessList.get(0).getTeamName());
        assertEquals(ResourceAccessEntry.LevelEnum.READER, accessList.get(0).getLevel());
    }

    private void createOrg(ApiClient client, String orgName) throws Exception {
        var organizationsApi = new OrganizationsApi(client);
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
    }

    private void createUsernamePasswordSecret(
            ApiClient client,
            String orgName,
            String secretName,
            SecretEntryV2.VisibilityEnum visibility
    ) throws Exception {
        var secretClient = new SecretClient(client);
        secretClient.createSecret(CreateSecretRequest.builder()
                .org(orgName)
                .name(secretName)
                .visibility(visibility)
                .usernamePassword(CreateSecretRequest.UsernamePassword.of("user", "pass"))
                .build());
    }

    private void createTeamWithUser(ApiClient client, String orgName, String teamName) throws Exception {
        var teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        var userName = "user_" + ITUtils.randomString();
        var usersApi = new UsersApi(client);
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        teamsApi.addUsersToTeam(orgName, teamName, false,
                Collections.singletonList(new TeamUserEntry()
                        .username(userName)
                        .role(TeamUserEntry.RoleEnum.MEMBER)));
    }
}
