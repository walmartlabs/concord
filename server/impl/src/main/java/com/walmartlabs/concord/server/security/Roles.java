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

public final class Roles {

    /**
     * Admins can access any resource and perform any action.
     */
    public static final String ADMIN = "concordAdmin";

    /**
     * System readers can access any resource.
     */
    public static final String SYSTEM_READER = "concordSystemReader";

    /**
     * System readers can modify any resource.
     */
    public static final String SYSTEM_WRITER = "concordSystemWriter";

    public static boolean isAdmin() {
        return SecurityUtils.hasRole(ADMIN);
    }

    public static boolean isGlobalReader() {
        return SecurityUtils.hasRole(SYSTEM_READER);
    }

    public static boolean isGlobalWriter() {
        return SecurityUtils.hasRole(SYSTEM_WRITER);
    }

    private Roles() {
    }
}
