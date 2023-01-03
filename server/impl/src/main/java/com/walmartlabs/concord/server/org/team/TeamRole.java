package com.walmartlabs.concord.server.org.team;

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


public enum TeamRole {

    /**
     * Can change the organization's settings, create new teams, etc.
     */
    OWNER,

    /**
     * Can add or remove other users to/from the team.
     */
    MAINTAINER,

    /**
     * Can access all team resources.
     */
    MEMBER;

    public static TeamRole[] atLeast(TeamRole r) {
        switch (r) {
            case OWNER:
                return new TeamRole[]{OWNER};
            case MAINTAINER:
                return new TeamRole[]{OWNER, MAINTAINER};
            case MEMBER:
                return new TeamRole[]{OWNER, MAINTAINER, MEMBER};
            default:
                throw new IllegalArgumentException("Unknown role: " + r);
        }
    }
}
