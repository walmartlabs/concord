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

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.console.Utils.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class TeamIT {

    @RegisterExtension
    public static ConcordServerRule serverRule = new ConcordServerRule();

    @RegisterExtension
    public static ConcordConsoleRule consoleRule = new ConcordConsoleRule();

    @Test
    public void testViewTeam() throws Exception {
        ApiClient client = serverRule.getClient();

        // --- setup: create org and team via API

        String orgName = "org_" + ITUtils.randomString();
        String teamName = "team_" + ITUtils.randomString();
        String teamDescription = "Test team description";

        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        TeamsApi teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName)
                .description(teamDescription));

        // --- login and navigate to team page

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/team/" + teamName);
        consoleRule.waitForLoad();

        // --- verify team page loads with correct tabs

        WebElement membersTab = consoleRule.waitFor(By.cssSelector("[data-testid='team-tab-members']"));
        assertNotNull(membersTab);
        assertTrue(membersTab.getText().contains("Members"));

        WebElement ldapGroupsTab = consoleRule.waitFor(By.cssSelector("[data-testid='team-tab-ldapGroups']"));
        assertNotNull(ldapGroupsTab);

        WebElement settingsTab = consoleRule.waitFor(By.cssSelector("[data-testid='team-tab-settings']"));
        assertNotNull(settingsTab);

        // --- navigate to settings and verify team ID is displayed

        settingsTab.click();
        consoleRule.waitForLoad();
        Thread.sleep(500);

        WebElement teamIdElement = consoleRule.waitFor(By.cssSelector("[data-testid='team-settings-id']"));
        assertNotNull(teamIdElement);
        assertTrue(teamIdElement.getText().contains("ID:"));
    }

    @Test
    public void testCreateTeam() throws Exception {
        ApiClient client = serverRule.getClient();

        // --- setup: create org via API

        String orgName = "org_" + ITUtils.randomString();
        String newTeamName = "team_" + ITUtils.randomString();
        String newTeamDescription = "New team description";

        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // --- login and navigate to new team page

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/team/_new");
        consoleRule.waitForLoad();

        // --- fill in the form

        WebElement nameInput = consoleRule.waitFor(By.cssSelector("[data-testid='team-form-name'] input"));
        nameInput.sendKeys(newTeamName);

        WebElement descriptionInput = consoleRule.waitFor(By.cssSelector("[data-testid='team-form-description'] input"));
        descriptionInput.sendKeys(newTeamDescription);

        Thread.sleep(500); // wait for validation

        // --- submit the form

        WebElement createButton = consoleRule.waitFor(By.cssSelector("[data-testid='team-form-submit']"));
        assertTrue(createButton.isEnabled());
        createButton.click();

        // --- wait for navigation to the new team page

        Thread.sleep(2000);
        consoleRule.waitForLoad();

        // --- verify we're on the new team's page

        String currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/org/" + orgName + "/team/" + newTeamName),
                "Expected URL to contain team path, but was: " + currentUrl);

        // --- verify team was created via API

        TeamsApi teamsApi = new TeamsApi(client);
        TeamEntry team = teamsApi.getTeam(orgName, newTeamName);
        assertNotNull(team);
        assertEquals(newTeamName, team.getName());
        assertEquals(newTeamDescription, team.getDescription());
    }

    @Test
    public void testRenameTeam() throws Exception {
        ApiClient client = serverRule.getClient();

        // --- setup: create org and team via API

        String orgName = "org_" + ITUtils.randomString();
        String originalTeamName = "team_" + ITUtils.randomString();
        String newTeamName = "renamed_" + ITUtils.randomString();

        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        TeamsApi teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(originalTeamName));

        // --- login and navigate to team settings

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/team/" + originalTeamName + "/settings");
        consoleRule.waitForLoad();

        // --- find the rename input, select all text, then enter new name

        WebElement renameInput = consoleRule.waitFor(By.cssSelector("[data-testid='team-rename-input'] input"));
        renameInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
        renameInput.sendKeys(newTeamName);

        Thread.sleep(500); // wait for validation

        // --- click rename button

        WebElement renameButton = consoleRule.waitFor(By.cssSelector("[data-testid='team-rename-button']"));
        assertTrue(renameButton.isEnabled());
        renameButton.click();

        // --- wait for confirmation modal and confirm

        Thread.sleep(500);
        WebElement confirmButton = consoleRule.waitFor(By.cssSelector(".ui.modal.active .actions .ui.primary.button"));
        confirmButton.click();

        // --- wait for navigation

        Thread.sleep(2000);
        consoleRule.waitForLoad();

        // --- verify we're on the renamed team's page

        String currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/org/" + orgName + "/team/" + newTeamName),
                "Expected URL to contain new team name, but was: " + currentUrl);

        // --- verify team was renamed via API

        TeamEntry team = teamsApi.getTeam(orgName, newTeamName);
        assertNotNull(team);
        assertEquals(newTeamName, team.getName());

        // --- verify old team name no longer exists

        try {
            teamsApi.getTeam(orgName, originalTeamName);
            fail("Expected team with original name to not exist");
        } catch (ApiException e) {
            // Accept any 4xx error - team should not be accessible
            assertTrue(e.getCode() >= 400 && e.getCode() < 500,
                    "Expected 4xx error but got: " + e.getCode());
        }
    }

    @Test
    public void testDeleteTeam() throws Exception {
        ApiClient client = serverRule.getClient();

        // --- setup: create org and team via API

        String orgName = "org_" + ITUtils.randomString();
        String teamName = "team_" + ITUtils.randomString();

        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        TeamsApi teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        // --- verify team exists

        TeamEntry team = teamsApi.getTeam(orgName, teamName);
        assertNotNull(team);

        // --- login and navigate to team settings

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/team/" + teamName + "/settings");
        consoleRule.waitForLoad();

        // --- click delete button

        WebElement deleteButton = consoleRule.waitFor(By.cssSelector("[data-testid='team-delete-button']"));
        deleteButton.click();

        // --- wait for confirmation modal and confirm

        Thread.sleep(500);
        WebElement confirmButton = consoleRule.waitFor(By.cssSelector(".ui.modal.active .actions .ui.primary.button"));
        confirmButton.click();

        // --- wait for navigation to team list

        Thread.sleep(2000);
        consoleRule.waitForLoad();

        // --- verify we're redirected to team list

        String currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/org/" + orgName + "/team") && !currentUrl.contains(teamName),
                "Expected URL to be team list, but was: " + currentUrl);

        // --- verify team was deleted via API

        try {
            teamsApi.getTeam(orgName, teamName);
            fail("Expected team to be deleted");
        } catch (ApiException e) {
            // Accept any 4xx error - team should not be accessible
            assertTrue(e.getCode() >= 400 && e.getCode() < 500,
                    "Expected 4xx error but got: " + e.getCode());
        }
    }

    @Test
    public void testTeamListNavigation() throws Exception {
        ApiClient client = serverRule.getClient();

        // --- setup: create org and teams via API

        String orgName = "org_" + ITUtils.randomString();
        String team1Name = "team_a_" + ITUtils.randomString();
        String team2Name = "team_b_" + ITUtils.randomString();

        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        TeamsApi teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(team1Name));
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(team2Name));

        // --- login and navigate to team list

        consoleRule.login(Concord.ADMIN_API_KEY);
        consoleRule.navigateToRelative("/#/org/" + orgName + "/team");
        consoleRule.waitForLoad();

        // --- verify both teams are listed

        WebElement team1Link = consoleRule.waitFor(By.linkText(team1Name));
        assertNotNull(team1Link);

        WebElement team2Link = consoleRule.waitFor(By.linkText(team2Name));
        assertNotNull(team2Link);

        // --- click on team1 and verify navigation

        team1Link.click();
        consoleRule.waitForLoad();
        Thread.sleep(500);

        String currentUrl = consoleRule.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("/org/" + orgName + "/team/" + team1Name),
                "Expected URL to contain team1 path, but was: " + currentUrl);
    }
}
