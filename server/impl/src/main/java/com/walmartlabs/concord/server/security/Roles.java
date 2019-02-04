package com.walmartlabs.concord.server.security;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;

public final class Roles {

    public static final String ADMIN = "concordAdmin";
    public static final String SYSTEM_READER = "concordSystemReader";
    public static final String SYSTEM_WRITER = "concordSystemWriter";

    public static boolean isAdmin() {
        Subject s = SecurityUtils.getSubject();
        return s.hasRole(ADMIN);
    }

    public static boolean isGlobalReader() {
        Subject s = SecurityUtils.getSubject();
        return s.hasRole(SYSTEM_READER);
    }

    public static boolean isGlobalWriter() {
        Subject s = SecurityUtils.getSubject();
        return s.hasRole(SYSTEM_WRITER);
    }

    private Roles() {
    }
}
