package com.walmartlabs.concord.server.security;

import java.io.Serializable;
import java.util.Set;

public class User implements Serializable {

    private final String id;
    private final String name;
    private final Set<String> permissions;

    public User(String id, String name, Set<String> permissions) {
        this.id = id;
        this.name = name;
        this.permissions = permissions;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
