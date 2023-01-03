package com.walmartlabs.concord.server.org;

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


public enum ResourceAccessLevel {

    /**
     * Can use, modify or delete the resource.
     */
    OWNER,

    /**
     * Can use or modify the resource.
     */
    WRITER,

    /**
     * Can use the resource.
     */
    READER;

    /**
     * @return an array of access levels that are the same or higher as the specified level.
     */
    public static ResourceAccessLevel[] atLeast(ResourceAccessLevel r) {
        switch (r) {
            case OWNER:
                return new ResourceAccessLevel[]{OWNER};
            case WRITER:
                return new ResourceAccessLevel[]{OWNER, WRITER};
            case READER:
                return new ResourceAccessLevel[]{OWNER, WRITER, READER};
            default:
                throw new IllegalArgumentException("Unknown access level: " + r);
        }
    }
}
