package com.walmartlabs.concord.plugins.ansible;

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class Resources {

    private static final String LIB_LOCATION = "/com/walmartlabs/concord/plugins/ansible/lib";
    private static final String[] LIBS = new String[]{"task_policy.py", "concord_ansible_stats.py"};

    private static final String STRATEGY_LOCATION = "/com/walmartlabs/concord/plugins/ansible/strategy";
    private static final String[] STRATEGIES = new String[]{"concord_free.py", "concord_linear.py"};

    private static final String CALLBACK_LOCATION = "/com/walmartlabs/concord/plugins/ansible/callback";
    private static final String[] CALLBACKS = new String[]{"concord_events.py",
            "concord_trace.py", "concord_protectdata.py", "concord_strategy_enforce.py", "concord_out_vars.py"};

    private static final String LOOKUP_LOCATION = "/com/walmartlabs/concord/plugins/ansible/lookup";
    private static final String[] LOOKUPS = new String[]{"concord_data_secret.py",
            "concord_inventory.py", "concord_public_key_secret.py", "concord_secret.py"};

    public static void copyLibs(Path libDir) throws IOException {
        copyResources(LIB_LOCATION, LIBS, libDir);
    }

    public static void copyCallback(Path callbackDir) throws IOException {
        copyResources(CALLBACK_LOCATION, CALLBACKS, callbackDir);
    }

    public static void copyLookups(Path lookupDir) throws IOException {
        copyResources(LOOKUP_LOCATION, LOOKUPS, lookupDir);
    }

    public static void copyStrategy(Path strategyDir) throws IOException {
        copyResources(STRATEGY_LOCATION, STRATEGIES, strategyDir);
    }

    private static void copyResources(String resourcesLocation, String[] files, Path dest) throws IOException {
        Files.createDirectories(dest);

        for (String f : files) {
            copyResourceToFile(Paths.get(resourcesLocation, f).toString(), dest.resolve(f));
        }
    }

    private static void copyResourceToFile(String resourceName, Path dest) throws IOException {
        try (InputStream is = RunPlaybookTask2.class.getResourceAsStream(resourceName)) {
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Resources() {
    }
}
