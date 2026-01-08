package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.PrivilegedAction;

import java.io.FilePermission;
import java.security.Permission;

public class ConcordSecurityManager extends SecurityManager {

    private static final String ALL_FILES_TOKEN = "<<ALL FILES>>"; // special java token

    private static final String[] ALLOWED_DOMAINS = {
            "docker",
            "task"
    };

    private static final String[] ALLOWED_PATHS = {
            System.getProperty("java.home"),             // JAVA_HOME
            System.getProperty("user.dir"),              // process' working directory
            PathUtils.TMP_DIR.toAbsolutePath().toString(), // concord's own TMP
            "/tmp",                                      // system TMP
            "/dev/", "/proc/", "/sys/",                  // unix stuff
            "/groovy/"                                   // Groovy's pseudo-files
    };

    @Override
    public void checkPermission(Permission perm) {
        if (perm instanceof FilePermission) {
            // allow our own "domains"
            String domain = PrivilegedAction.getCurrentDomain();
            if (domain != null) {
                for (String d : ALLOWED_DOMAINS) {
                    if (d.equals(domain)) {
                        return;
                    }
                }
            }

            String path = perm.getName();

            // allow all local paths
            if (!path.startsWith("/") && !path.equals(ALL_FILES_TOKEN)) {
                return;
            }

            // (crude) allow dependencies
            if (path.endsWith(".jar")) {
                return;
            }

            for (String p : ALLOWED_PATHS) {
                if (path.startsWith(p)) {
                    return;
                }
            }

            // disallow anything else
            System.err.println("FORBIDDEN: " + path);
            throw new SecurityException("Forbidden: " + path);
        }
    }

    @Override
    public void checkPermission(Permission perm, Object context) {
        checkPermission(perm);
    }
}
