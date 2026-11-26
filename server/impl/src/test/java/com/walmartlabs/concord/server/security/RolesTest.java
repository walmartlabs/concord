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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RolesTest {

    @Test
    public void testRoleConstants() {
        assertEquals("concordAdmin", Roles.ADMIN);
        assertEquals("concordModerator", Roles.MODERATOR);
        assertEquals("concordSystemReader", Roles.SYSTEM_READER);
        assertEquals("concordSystemWriter", Roles.SYSTEM_WRITER);
    }

    @Test
    public void testRoleConstantsNotEmpty() {
        assertNotNull(Roles.ADMIN);
        assertNotNull(Roles.MODERATOR);
        assertNotNull(Roles.SYSTEM_READER);
        assertNotNull(Roles.SYSTEM_WRITER);
    }
}
