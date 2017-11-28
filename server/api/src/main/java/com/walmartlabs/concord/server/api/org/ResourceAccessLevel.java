package com.walmartlabs.concord.server.api.org;

public enum ResourceAccessLevel {

    /**
     * Can use, modify or delete the resource.
     */
    OWNER,

    /**
     * Can use or modify the resource.
     */
    WRITER,

    /**
     * Can use the resource.
     */
    READER;

    public static ResourceAccessLevel[] atLeast(ResourceAccessLevel r) {
        switch (r) {
            case OWNER:
                return new ResourceAccessLevel[]{OWNER};
            case WRITER:
                return new ResourceAccessLevel[]{OWNER, WRITER};
            case READER:
                return new ResourceAccessLevel[]{OWNER, WRITER, READER};
            default:
                throw new IllegalArgumentException("Unknown access level: " + r);
        }
    }
}
