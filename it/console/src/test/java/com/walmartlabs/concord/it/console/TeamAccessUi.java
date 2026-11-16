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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.it.common.ITUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;

import java.util.Collections;

/**
 * Helpers for the shared team-access UI (the {@code /access} tab of a project
 * or a secret) and its usual API-side setup.
 */
final class TeamAccessUi {

    /**
     * Starts edit mode of the team access table.
     */
    static void startEditing(ConcordConsoleRule console) throws InterruptedException {
        var editButton = console.waitFor(By.cssSelector("[data-testid='team-access-edit-btn']"));
        editButton.click();
        Thread.sleep(500);
    }

    /**
     * Selects a team in the "add team" dropdown, returns the new row.
     */
    static WebElement addTeam(ConcordConsoleRule console, String teamName) throws InterruptedException {
        var teamDropdown = console.waitFor(By.cssSelector("[data-testid='team-access-add-dropdown'] input"));
        teamDropdown.click();
        new Actions(console.getDriver())
                .sendKeys(teamName)
                .pause(500)
                .sendKeys(Keys.ENTER)
                .perform();
        Thread.sleep(500);

        return console.waitFor(By.cssSelector("[data-testid='team-access-row-" + teamName + "']"));
    }

    /**
     * Saves the pending changes of the team access table.
     */
    static void save(ConcordConsoleRule console) throws InterruptedException {
        var saveButton = console.waitFor(By.cssSelector("[data-testid='team-access-save-btn']:not([disabled])"));
        saveButton.click();
        Thread.sleep(1500);
    }

    /**
     * Creates a team with a fresh local user as a member.
     */
    static void createTeamWithUser(ApiClient client, String orgName, String teamName) throws Exception {
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

    private TeamAccessUi() {
    }
}
