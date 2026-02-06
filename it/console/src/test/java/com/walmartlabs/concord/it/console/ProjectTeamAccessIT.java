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
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectTeamAccessIT extends AbstractConsoleIT {

    @Test
    public void testAddTeamAccess() throws Exception {
        ApiClient client = getApiClient();

        // ---

        String orgName = "org_" + ITUtils.randomString();
        String projectName = "project_" + ITUtils.randomString();
        createOrgAndProject(client, orgName, projectName);

        String team1Name = "team_" + ITUtils.randomString();
        String team2Name = "team_" + ITUtils.randomString();
        createTeamWithUser(client, orgName, team1Name);
        createTeamWithUser(client, orgName, team2Name);

        assignTeamAccess(client, orgName, projectName, team1Name, ResourceAccessEntry.LevelEnum.READER);

        // ---

        login(defaultApiKey());
        navigateToRelative("/#/org/" + orgName + "/project/" + projectName + "/access");
        waitForLoad();

        // ---

        WebElement team1Row = waitFor(By.cssSelector("[data-testid='team-access-row-" + team1Name + "']"));
        assertNotNull(team1Row);
        WebElement team1AccessCell = waitFor(By.cssSelector("[data-testid='team-access-level-" + team1Name + "']"));
        assertEquals("READER", team1AccessCell.getText());

        // ---

        WebElement editButton = waitFor(By.cssSelector("[data-testid='team-access-edit-btn']"));
        editButton.click();
        Thread.sleep(500);

        // ---

        WebElement teamDropdown = waitFor(By.cssSelector("[data-testid='team-access-add-dropdown'] input"));
        teamDropdown.click();
        new Actions(getDriver())
                .sendKeys(team2Name)
                .pause(500)
                .sendKeys(Keys.ENTER)
                .perform();
        Thread.sleep(500);

        WebElement team2Row = waitFor(By.cssSelector("[data-testid='team-access-row-" + team2Name + "']"));
        assertTrue(team2Row.getDomAttribute("class").contains("positive"));

        // ---

        WebElement saveButton = waitFor(By.cssSelector("[data-testid='team-access-save-btn']:not([disabled])"));
        saveButton.click();
        Thread.sleep(1500);

        // ---

        waitFor(By.cssSelector("[data-testid='team-access-row-" + team1Name + "']"));
        waitFor(By.cssSelector("[data-testid='team-access-row-" + team2Name + "']"));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(client);
        List<ResourceAccessEntry> accessList = projectsApi.getProjectAccessLevel(orgName, projectName);
        assertEquals(2, accessList.size());
    }

    @Test
    public void testChangeAccessLevel() throws Exception {
        ApiClient client = getApiClient();

        // ---

        String orgName = "org_" + ITUtils.randomString();
        String projectName = "project_" + ITUtils.randomString();
        createOrgAndProject(client, orgName, projectName);

        String teamName = "team_" + ITUtils.randomString();
        createTeamWithUser(client, orgName, teamName);

        assignTeamAccess(client, orgName, projectName, teamName, ResourceAccessEntry.LevelEnum.READER);

        // ---

        login(defaultApiKey());
        navigateToRelative("/#/org/" + orgName + "/project/" + projectName + "/access");
        waitForLoad();

        // ---

        WebElement accessCell = waitFor(By.cssSelector("[data-testid='team-access-level-" + teamName + "']"));
        assertEquals("READER", accessCell.getText());

        // ---

        WebElement editButton = waitFor(By.cssSelector("[data-testid='team-access-edit-btn']"));
        editButton.click();
        Thread.sleep(500);

        // ---

        WebElement dropdown = waitFor(By.cssSelector("[data-testid='team-access-dropdown-" + teamName + "']"));
        dropdown.click();
        Thread.sleep(800);

        WebElement ownerOption = waitFor(By.xpath("//div[@role='listbox']//div[@role='option' and .//span[text()='Owner']]"));
        ownerOption.click();
        Thread.sleep(300);

        // ---

        WebElement saveButton = waitFor(By.cssSelector("[data-testid='team-access-save-btn']"));
        saveButton.click();
        Thread.sleep(1500);

        // ---

        accessCell = waitFor(By.cssSelector("[data-testid='team-access-level-" + teamName + "']"));
        assertEquals("OWNER", accessCell.getText());

        // ---

        ProjectsApi projectsApi = new ProjectsApi(client);
        List<ResourceAccessEntry> accessList = projectsApi.getProjectAccessLevel(orgName, projectName);
        assertEquals(1, accessList.size());
        assertEquals(ResourceAccessEntry.LevelEnum.OWNER, accessList.get(0).getLevel());
    }

    @Test
    public void testRemoveTeamAccess() throws Exception {
        ApiClient client = getApiClient();

        // ---

        String orgName = "org_" + ITUtils.randomString();
        String projectName = "project_" + ITUtils.randomString();
        createOrgAndProject(client, orgName, projectName);

        String team1Name = "team_" + ITUtils.randomString();
        String team2Name = "team_" + ITUtils.randomString();
        createTeamWithUser(client, orgName, team1Name);
        createTeamWithUser(client, orgName, team2Name);

        assignTeamAccess(client, orgName, projectName, team1Name, ResourceAccessEntry.LevelEnum.READER);
        assignTeamAccess(client, orgName, projectName, team2Name, ResourceAccessEntry.LevelEnum.WRITER);

        // ---

        login(defaultApiKey());
        navigateToRelative("/#/org/" + orgName + "/project/" + projectName + "/access");
        waitForLoad();

        // ---

        waitFor(By.cssSelector("[data-testid='team-access-row-" + team1Name + "']"));
        waitFor(By.cssSelector("[data-testid='team-access-row-" + team2Name + "']"));

        // ---

        WebElement editButton = waitFor(By.cssSelector("[data-testid='team-access-edit-btn']"));
        editButton.click();
        Thread.sleep(500);

        // ---

        WebElement team1Row = waitFor(By.cssSelector("[data-testid='team-access-row-" + team1Name + "']"));
        WebElement deleteButton = waitFor(By.cssSelector("[data-testid='team-access-delete-btn-" + team1Name + "']"));
        deleteButton.click();
        Thread.sleep(300);
        assertTrue(team1Row.getDomAttribute("class").contains("negative"));

        deleteButton.click();
        Thread.sleep(300);
        assertFalse(team1Row.getDomAttribute("class").contains("negative"));

        deleteButton.click();
        Thread.sleep(300);
        assertTrue(team1Row.getDomAttribute("class").contains("negative"));

        // ---

        WebElement saveButton = waitFor(By.cssSelector("[data-testid='team-access-save-btn']"));
        saveButton.click();
        Thread.sleep(1500);

        // ---

        List<WebElement> team1Rows = getDriver().findElements(By.cssSelector("[data-testid='team-access-row-" + team1Name + "']"));
        assertEquals(0, team1Rows.size());
        waitFor(By.cssSelector("[data-testid='team-access-row-" + team2Name + "']"));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(client);
        List<ResourceAccessEntry> accessList = projectsApi.getProjectAccessLevel(orgName, projectName);
        assertEquals(1, accessList.size());
        assertEquals(team2Name, accessList.get(0).getTeamName());
    }

    private void createOrgAndProject(ApiClient client, String orgName, String projectName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(client);
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(client);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry().name(projectName));
    }

    private void createTeamWithUser(ApiClient client, String orgName, String teamName) throws Exception {
        TeamsApi teamsApi = new TeamsApi(client);
        teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));

        String userName = "user_" + ITUtils.randomString();
        UsersApi usersApi = new UsersApi(client);
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        teamsApi.addUsersToTeam(orgName, teamName, false,
                Collections.singletonList(new TeamUserEntry()
                        .username(userName)
                        .role(TeamUserEntry.RoleEnum.MEMBER)));
    }

    private void assignTeamAccess(ApiClient client, String orgName, String projectName,
                                  String teamName, ResourceAccessEntry.LevelEnum level) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(client);
        projectsApi.updateProjectAccessLevel(orgName, projectName,
                new ResourceAccessEntry()
                        .orgName(orgName)
                        .teamName(teamName)
                        .level(level));
    }
}
