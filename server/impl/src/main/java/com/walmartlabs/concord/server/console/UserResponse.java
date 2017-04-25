package com.walmartlabs.concord.server.console;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(Include.NON_NULL)
public class UserResponse implements Serializable {

    private final String realm;
    private final String username;
    private final String displayName;

    @JsonCreator
    public UserResponse(@JsonProperty("realm") String realm,
                        @JsonProperty("username") String username,
                        @JsonProperty("displayName") String displayName) {

        this.realm = realm;
        this.username = username;
        this.displayName = displayName;
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

    @Override
    public String toString() {
        return "UserResponse{" +
                "displayName='" + displayName + '\'' +
                '}';
    }
}
