package com.walmartlabs.concord.server.api.team;

import java.io.Serializable;

public class AddTeamUsersResponse implements Serializable {

    private final boolean ok = true;

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "AddTeamUsersResponse{" +
                "ok=" + ok +
                '}';
    }
}
