package com.walmartlabs.concord.project.yaml;

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

import com.walmartlabs.concord.project.model.Resources;
import com.walmartlabs.concord.sdk.Constants;

import java.util.*;

import static com.walmartlabs.concord.sdk.Constants.Files.PROFILES_DIR_NAME;
import static com.walmartlabs.concord.sdk.Constants.Files.PROJECT_FILES_DIR_NAME;

public final class YamlResourcesConverter {

    @SuppressWarnings("unchecked")
    public static Resources parse(Map<String, Object> m) {
        List<String> disabledDirs = (List<String>) m.getOrDefault("disabled", Collections.emptyList());

        List<String> profilesPath = getPaths(m, disabledDirs, PROFILES_DIR_NAME);
        List<String> projectFilesPath = getPaths(m, disabledDirs, PROJECT_FILES_DIR_NAME);
        List<String> definitionPath = getPaths(m, disabledDirs, Constants.Files.DEFINITIONS_DIR_NAMES);

        return new Resources(profilesPath, projectFilesPath, definitionPath, disabledDirs);
    }

    @SuppressWarnings("unchecked")
    private static List<String> getPaths(Map<String, Object> resources, Collection<String> disabledDirs, String ... definitionsDirNames) {
        List<String> result = new ArrayList<>();

        for (String dirName : definitionsDirNames) {
            Object v = resources.get(dirName);
            if (v == null && !disabledDirs.contains(dirName)) {
                result.add(dirName);
            } else if (v instanceof String) {
                result.add((String) v);
            } else if (v instanceof Collection) {
                result.addAll((Collection<String>) v);
            }
        }

        return result;
    }

    private YamlResourcesConverter() {
    }
}
