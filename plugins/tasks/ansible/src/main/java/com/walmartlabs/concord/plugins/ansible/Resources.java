package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.plugins.ansible.v1.RunPlaybookTask2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public final class Resources {

    public static void copy(String resourcesLocation, String[] files, Path dest) throws IOException {
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
