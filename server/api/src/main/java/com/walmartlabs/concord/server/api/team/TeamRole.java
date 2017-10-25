package com.walmartlabs.concord.server.api.team;

public enum TeamRole {

    OWNER,
    WRITER,
    READER;

    public static TeamRole[] atLeast(TeamRole r) {
        switch (r) {
            case OWNER:
                return new TeamRole[]{OWNER};
            case WRITER:
                return new TeamRole[]{OWNER, WRITER};
            case READER:
                return new TeamRole[]{OWNER, WRITER, READER};
            default:
                throw new IllegalArgumentException("Unknown role: " + r);
        }
    }
}
