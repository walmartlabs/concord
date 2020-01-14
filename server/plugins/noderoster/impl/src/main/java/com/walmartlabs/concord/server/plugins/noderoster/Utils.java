package com.walmartlabs.concord.server.plugins.noderoster;

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

import org.sonatype.siesta.ValidationErrorsException;

import java.util.UUID;

public final class Utils {

    public static String[] toString(Enum<?>... e) {
        String[] as = new String[e.length];
        for (int i = 0; i < e.length; i++) {
            as[i] = e[i].toString();
        }
        return as;
    }

    // TODO should be a part of HostManager
    public static UUID getHostId(HostManager hostManager, UUID hostId, String hostName) {
        if (hostId == null && hostName == null) {
            throw new ValidationErrorsException("A 'hostName' or 'hostId' value is required");
        }

        if (hostId != null) {
            return hostId;
        }

        return hostManager.getId(hostName);
    }

    private Utils() {
    }
}
