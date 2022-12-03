package com.walmartlabs.concord.server.user;

import com.walmartlabs.concord.server.org.team.TeamRole;
import org.immutables.value.Value;

import java.util.UUID;

@Value.Immutable
public interface UserTeam {

    UUID teamId();

    TeamRole role();

    static UserTeam of(UUID teamId, TeamRole role) {
        return UserTeam.builder()
                .teamId(teamId)
                .role(role)
                .build();
    }

    static ImmutableUserTeam.Builder builder() {
        return ImmutableUserTeam.builder();
    }
}
