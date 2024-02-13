package com.walmartlabs.concord.it.server;

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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JsonStoreIT extends AbstractServerIT {

    @Test
    public void testValidationJsonStoreRequest() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        JsonStoreApi api = new JsonStoreApi(getApiClient());
        try {
            api.createOrUpdateJsonStore(orgName, new JsonStoreRequest().name("<script></script>"));
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(400, e.getCode());
            assertEquals("[{\"id\":\"ImmutableJsonStoreRequest.createOrUpdate.arg1.name\",\"message\":\"must match \\\"^[0-9a-zA-Z][0-9a-zA-Z_@.\\\\-~]{2,128}$\\\"\"}]", e.getResponseBody());
        }
    }

    @Test
    public void testBulkAccessUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String storeName = "store_" + randomString();

        JsonStoreApi jsonStoreApi = new JsonStoreApi(getApiClient());
        jsonStoreApi.createOrUpdateJsonStore(orgName, new JsonStoreRequest()
                .name(storeName));

        // ---

        String teamName = "team_" + randomString();
        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse teamResp = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName));

        // --- Typical one-or-more teams bulk access update

        List<ResourceAccessEntry> teams = new ArrayList<>(1);
        teams.add(new ResourceAccessEntry()
                .orgName(orgName)
                .teamId(teamResp.getId())
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.OWNER));
        GenericOperationResult addTeamsResult = jsonStoreApi.bulkUpdateJsonStoreAccessLevel(orgName, storeName, teams);
        assertNotNull(addTeamsResult);
        assertTrue(addTeamsResult.getOk());

        List<ResourceAccessEntry> currentTeams = jsonStoreApi.getJsonStoreAccessLevel(orgName, storeName);
        assertNotNull(currentTeams);
        assertEquals(1, currentTeams.size());

        // --- Empty teams list clears all

        GenericOperationResult clearTeamsResult = jsonStoreApi.bulkUpdateJsonStoreAccessLevel(orgName, storeName, Collections.emptyList());
        assertNotNull(clearTeamsResult);
        assertTrue(clearTeamsResult.getOk());

        currentTeams = jsonStoreApi.getJsonStoreAccessLevel(orgName, storeName);
        assertNotNull(currentTeams);
        assertEquals(0, currentTeams.size());

        // --- Null list not allowed, throws error

        try {
            jsonStoreApi.bulkUpdateJsonStoreAccessLevel(orgName, storeName, null);
        } catch (ApiException expected) {
            assertEquals(400, expected.getCode());
            assertTrue(expected.getResponseBody().contains("List of teams is null"));
        } catch (Exception e) {
            fail("Expected ApiException. Got " + e.getClass().toString());
        }

        // ---

        teamsApi.deleteTeam(orgName, teamName);
        jsonStoreApi.deleteJsonStore(orgName, storeName);
        orgApi.deleteOrg(orgName, "yes");
    }
}
