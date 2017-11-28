package com.walmartlabs.concord.server.api.org.team;

public enum TeamRole {

    /**
     * Can change the organization's settings, create new teams, etc.
     */
    OWNER,

    /**
     * Can add or remove other users to/from the team.
     */
    MAINTAINER,

    /**
     * Can access all team resources.
     */
    MEMBER;

    public static TeamRole[] atLeast(TeamRole r) {
        switch (r) {
            case OWNER:
                return new TeamRole[]{OWNER};
            case MAINTAINER:
                return new TeamRole[]{OWNER, MAINTAINER};
            case MEMBER:
                return new TeamRole[]{OWNER, MAINTAINER, MEMBER};
            default:
                throw new IllegalArgumentException("Unknown role: " + r);
        }
    }
}
