package com.walmartlabs.concord.server.plugins.noderoster;

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

import com.walmartlabs.concord.common.StringUtils;

import javax.inject.Named;

import static com.walmartlabs.concord.server.plugins.noderoster.jooq.tables.NodeRosterHosts.NODE_ROSTER_HOSTS;

@Named
public class HostNormalizer {

    public String normalize(String host) {
        // TODO implement, add metrics
        return StringUtils.abbreviate(host.toLowerCase(), NODE_ROSTER_HOSTS.NORMALIZED_HOSTNAME.getDataType().length());
    }
}
