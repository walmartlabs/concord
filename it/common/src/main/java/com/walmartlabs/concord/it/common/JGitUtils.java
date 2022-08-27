package com.walmartlabs.concord.it.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JGitUtils {

    private static final Logger log = LoggerFactory.getLogger(JGitUtils.class);

    public static void applyWorkarounds() {
        // avoid consuming any local git configs
        try {
            SystemReader.getInstance().getUserConfig().clear();
        } catch (Exception e) {
            log.warn("Failed while trying to clear JGit's user config: {}", e.getMessage());
        }
    }

    private JGitUtils() {
    }
}
