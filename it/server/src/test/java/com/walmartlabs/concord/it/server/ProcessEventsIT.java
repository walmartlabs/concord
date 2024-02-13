package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class ProcessEventsIT extends AbstractServerIT {

    @Test
    public void testIncludeAllPermissions() throws Exception {
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName));

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        String projectName = "project_" + randomString();
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.OWNERS));

        // ---

        byte[] payload = archive(ProcessEventsIT.class.getResource("runnerEvents").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("org", orgName);
        input.put("project", projectName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pe = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        // ---

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello!.*", ab);

        // ---

        ProcessEventsApi eventsApi = new ProcessEventsApi(getApiClient());

        List<ProcessEventEntry> events = eventsApi.listProcessEvents(pe.getInstanceId(), "ELEMENT", null, null, null, "pre", true, 10);
        assertEquals(1, events.size());

        ProcessEventEntry ev = events.get(0);
        assertInParameter(ev, "msg", "Hello!");

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());

        String userName = "user_" + randomString();
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(userName)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse car = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .userId(cur.getId()));

        // ---

        setApiKey(car.getKey());

        // ---

        try {
            eventsApi.listProcessEvents(pe.getInstanceId(), "ELEMENT", null, null, null, "pre", true, 10);
            fail("should fail");
        } catch (ApiException e) {
            assertEquals(403, e.getCode());
        }

        // ---

        resetApiKey();

        // ---

        TeamsApi teamsApi = new TeamsApi(getApiClient());

        String teamName = "team_" + randomString();
        CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName));

        teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                .userId(cur.getId())
                .role(TeamUserEntry.RoleEnum.MEMBER)));

        projectsApi.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                .teamId(ctr.getId())
                .level(ResourceAccessEntry.LevelEnum.WRITER));

        // ---

        setApiKey(car.getKey());

        events = eventsApi.listProcessEvents(pe.getInstanceId(), "ELEMENT", null, null, null, "pre", true, 10);
        assertEquals(1, events.size());

        ev = events.get(0);
        assertInParameter(ev, "msg", "Hello!");
    }

    @SuppressWarnings("unchecked")
    private static void assertInParameter(ProcessEventEntry ev, String target, String resolvedValue) {
        Map<String, Object> data = ev.getData();
        assertTrue(data.containsKey("in"));

        List<Map<String, Object>> in = (List<Map<String, Object>>) data.get("in");
        for (Map<String, Object> var : in) {
            String t = (String) var.get("target");
            String r = (String) var.get("resolved");
            if (target.equals(t) && resolvedValue.equals(r)) {
                return;
            }
        }

        fail("Can't find " + target + " -> '" + resolvedValue + "' in the event's in parameters");
    }
}
