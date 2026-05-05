package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.ldap.LdapPrincipal;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SecurityUtilsTest {

    @Test
    public void serializesPrincipalsAsJsonAndRestoresKnownTypes() {
        UUID userId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UserEntry user = new UserEntry(userId, "test-user", "example.org", "Test User", null, UserType.LDAP, "test@example.org",
                Set.of(new RoleEntry(UUID.randomUUID(), "testRole", Set.of("testPermission"))), false, null, false);
        LdapPrincipal ldap = new LdapPrincipal("test-user", "example.org", "cn=test-user,dc=example,dc=org", "test-user@example.org",
                "Test User", "test@example.org", Set.of("cn=devs"), Map.of("mail", "test@example.org"));
        ApiKey apiKey = new ApiKey(keyId, userId, "api-key", true);
        UsernamePasswordToken usernamePassword = new UsernamePasswordToken("test-user", "secret".toCharArray(), true);

        SimplePrincipalCollection src = new SimplePrincipalCollection();
        src.add(new UserPrincipal("ldap", user), "ldap");
        src.add(ldap, "ldap");
        src.add(apiKey, "apikey");
        src.add(usernamePassword, "ldap");

        byte[] bytes = SecurityUtils.serialize(src);
        assertEquals('{', new String(bytes, StandardCharsets.UTF_8).charAt(0));

        PrincipalCollection dst = SecurityUtils.deserialize(bytes).orElseThrow();
        UserPrincipal userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals(userId, userPrincipal.getId());
        assertEquals("testRole", userPrincipal.getUser().getRoles().iterator().next().getName());

        LdapPrincipal ldapPrincipal = dst.oneByType(LdapPrincipal.class);
        assertNotNull(ldapPrincipal);
        assertEquals("test-user", ldapPrincipal.getUsername());
        assertEquals(Set.of("cn=devs"), ldapPrincipal.getGroups());

        ApiKey restoredApiKey = dst.oneByType(ApiKey.class);
        assertNotNull(restoredApiKey);
        assertEquals(keyId, restoredApiKey.getKeyId());
        assertTrue(restoredApiKey.isRememberMe());

        UsernamePasswordToken restoredUsernamePassword = dst.oneByType(UsernamePasswordToken.class);
        assertNotNull(restoredUsernamePassword);
        assertEquals("test-user", restoredUsernamePassword.getUsername());
        assertArrayEquals("secret".toCharArray(), restoredUsernamePassword.getPassword());
        assertTrue(restoredUsernamePassword.isRememberMe());
    }

    @Test
    public void deserializesLegacyJavaPrincipalCollections() {
        UserEntry user = new UserEntry(UUID.randomUUID(), "legacy-user", null, null, null, UserType.LOCAL, null, null, false, null, false);
        SimplePrincipalCollection src = new SimplePrincipalCollection(new UserPrincipal("legacy", user), "legacy");

        byte[] bytes = PrincipalSerializer.legacySerialize(src);

        PrincipalCollection dst = SecurityUtils.deserialize(bytes).orElseThrow();
        UserPrincipal userPrincipal = dst.oneByType(UserPrincipal.class);
        assertNotNull(userPrincipal);
        assertEquals("legacy-user", userPrincipal.getUsername());
    }
}
