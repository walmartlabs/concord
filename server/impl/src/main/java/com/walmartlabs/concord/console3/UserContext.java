package com.walmartlabs.concord.console3;

import com.walmartlabs.concord.server.security.UserPrincipal;

import java.util.Optional;

public record UserContext(String username, String displayName, String email) {

    static Optional<UserContext> getCurrent() {
        return Optional.ofNullable(UserPrincipal.getCurrent())
                .map(p -> new UserContext(p.getUsername(),
                        p.getUser().getDisplayName(),
                        p.getUser().getEmail()));
    }
}
