package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Utility methods for working with Shiro's security context.
 * Should be the only place where Shiro's API is used directly except for
 * the security filters.
 */
public final class SecurityUtils {

    public static void logout() {
        Subject subject = getSubject();
        if (subject == null) {
            return;
        }
        subject.logout();
    }

    public static boolean hasRole(String role) {
        Subject s = getSubject();
        if (s == null) {
            return false;
        }
        return s.hasRole(role);
    }

    public static boolean isPermitted(String permission) {
        Subject s = getSubject();
        if (s == null) {
            return false;
        }
        return s.isPermitted(permission);
    }

    public static Subject getSubject() {
        return ThreadContext.getSubject();
    }

    public static Subject assertSubject() {
        Subject subject = getSubject();
        if (subject == null) {
            throw new AuthenticationException("Can't determine the current security subject");
        }
        return subject;
    }

    public static <T> T getPrincipal(Class<T> type) {
        SecurityManager securityManager = ThreadContext.getSecurityManager();
        if (securityManager == null) {
            return null;
        }

        Subject subject = getSubject();
        if (subject == null) {
            return null;
        }

        PrincipalCollection principals = subject.getPrincipals();
        if (principals == null) {
            return null;
        }

        return principals.oneByType(type);
    }

    public static <T> T assertPrincipal(Class<T> type) {
        T p = getPrincipal(type);
        if (p == null) {
            throw new AuthenticationException("Can't determine the current principal (" + type.getName() + ")");
        }
        return p;
    }

    public static byte[] serialize(PrincipalCollection data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    public static Optional<PrincipalCollection> deserialize(byte[] data) {
        InputStream in = new ByteArrayInputStream(data);
        return deserialize(in);
    }

    public static Optional<PrincipalCollection> deserialize(InputStream in) {
        try (ObjectInputStream ois = new ObjectInputStream(in)) {
            return Optional.of((PrincipalCollection) ois.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static AuthorizationInfo toAuthorizationInfo(PrincipalCollection principals) {
        return toAuthorizationInfo(principals, null);
    }

    public static AuthorizationInfo toAuthorizationInfo(PrincipalCollection principals, List<String> extraRoles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        UserPrincipal p = principals.oneByType(UserPrincipal.class);
        if (p == null) {
            return i;
        }

        UserEntry u = p.getUser();
        Set<RoleEntry> roles = u.getRoles();
        if (roles != null) {
            roles.forEach(r -> {
                i.addRole(r.getName());

                Set<String> permissions = r.getPermissions();
                if (permissions != null) {
                    permissions.forEach(i::addStringPermission);
                }
            });
        }

        if (extraRoles != null) {
            extraRoles.forEach(i::addRole);
        }

        return i;
    }

    private SecurityUtils() {
    }
}
