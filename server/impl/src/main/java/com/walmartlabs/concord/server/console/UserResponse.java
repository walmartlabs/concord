package com.walmartlabs.concord.server.console;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.team.TeamEntry;

import java.io.Serializable;
import java.util.Set;

@JsonInclude(Include.NON_EMPTY)
public class UserResponse implements Serializable {

    private final String realm;
    private final String username;
    private final String displayName;
    private final Set<TeamEntry> teams;

    @JsonCreator
    public UserResponse(@JsonProperty("realm") String realm,
                        @JsonProperty("username") String username,
                        @JsonProperty("displayName") String displayName,
                        @JsonProperty("teams") Set<TeamEntry> teams) {

        this.realm = realm;
        this.username = username;
        this.displayName = displayName;
        this.teams = teams;
    }

    public String getRealm() {
        return realm;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<TeamEntry> getTeams() {
        return teams;
    }

    @Override
    public String toString() {
        return "UserResponse{" +
                "realm='" + realm + '\'' +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", teams=" + teams +
                '}';
    }
}
