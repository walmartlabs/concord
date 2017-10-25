package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.team.*;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.UserResource;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TeamRolesIT extends AbstractServerIT {

    @Test
    public void testTeams() {
        // ---

        TeamResource teamResource = proxy(TeamResource.class);

        String teamName = "team_" + System.currentTimeMillis();
        CreateTeamResponse ctr = teamResource.createOrUpdate(new TeamEntry(null, teamName, null, null, TeamVisibility.PRIVATE));
        assertNotNull(ctr.getId());

        // ---

        UserResource userResource = proxy(UserResource.class);

        String userAName = "userA_" + System.currentTimeMillis();
        userResource.createOrUpdate(new CreateUserRequest(userAName));

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakrA = apiKeyResource.create(new CreateApiKeyRequest(null, userAName));

        setApiKey(cakrA.getKey());

        // ---

        List<TeamEntry> teams = teamResource.list();
        assertNull(findTeam(teams, teamName));

        // ---

        resetApiKey();
        teamResource.addUsers(teamName, Arrays.asList(new TeamUserEntry(userAName, TeamRole.READER)));

        // ---

        setApiKey(cakrA.getKey());

        teams = teamResource.list();
        assertNotNull(findTeam(teams, teamName));
    }

    private static TeamEntry findTeam(List<TeamEntry> l, String name) {
        return l.stream().filter(e -> name.equals(e.getName())).findAny().orElse(null);
    }
}
