package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SubjectContext;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class PasswordCheckerTest {

    private static final String USERNAME = "vasia";

    @BeforeEach
    public void bindUser() {
        SecurityManager securityManager = new DefaultSecurityManager();
        ThreadContext.bind(securityManager);

        UserPrincipal p = new UserPrincipal("test", new UserEntry(UUID.randomUUID(), USERNAME, null, null, null, null, null, null, false, null, false));
        SubjectContext ctx = new DefaultSubjectContext();
        ctx.setAuthenticated(true);
        ctx.setPrincipals(new SimplePrincipalCollection(p, p.getRealm()));

        Subject subject = securityManager.createSubject(ctx);
        ThreadContext.bind(subject);
    }

    @AfterEach
    public void unbindUser() {
        ThreadContext.unbindSubject();
    }

    @Test
    public void lengthTest() {
        String password = "aA3456";

        try {
            PasswordChecker.check(password);
            fail("exception expected");
        } catch (PasswordChecker.CheckerException e) {
            assertTrue(e.getMessage().contains("seven (7) characters"));
        }
    }

    @Test
    public void upperTest() {
        String password = "a234567";

        try {
            PasswordChecker.check(password);
            fail("exception expected");
        } catch (PasswordChecker.CheckerException e) {
            assertTrue(e.getMessage().contains("UPPER character"));
        }
    }

    @Test
    public void lowerTest() {
        String password = "A234567";

        try {
            PasswordChecker.check(password);
            fail("exception expected");
        } catch (PasswordChecker.CheckerException e) {
            assertTrue(e.getMessage().contains("lowercase character"));
        }
    }

    @Test
    public void digitTest() {
        String password = "AaAAAAA";

        try {
            PasswordChecker.check(password);
            fail("exception expected");
        } catch (PasswordChecker.CheckerException e) {
            assertTrue(e.getMessage().contains("numeric character"));
        }
    }

    // Passwords may NOT contain three (3) consecutive characters from your user account name.
    @Test
    public void userTest() {
        String password = "Aa345678asi";

        try {
            PasswordChecker.check(password);
            fail("exception expected");
        } catch (PasswordChecker.CheckerException e) {
            assertTrue(e.getMessage().contains("consecutive characters from your user account name"));
        }
    }

    @Test
    public void validTest() throws PasswordChecker.CheckerException {
        String password = "Aa345678";

        PasswordChecker.check(password);
    }
}
