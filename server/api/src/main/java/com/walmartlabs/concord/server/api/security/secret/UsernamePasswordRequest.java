package com.walmartlabs.concord.server.api.security.secret;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;

public class UsernamePasswordRequest implements Serializable {

    @NotNull
    private final String username;

    @NotNull
    private final char[] password;

    @JsonCreator
    public UsernamePasswordRequest(@JsonProperty("username") String username, @JsonProperty("password") char[] password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return "UsernamePasswordRequest{" +
                "username='" + username + '\'' +
                ", password='********'" +
                '}';
    }
}
