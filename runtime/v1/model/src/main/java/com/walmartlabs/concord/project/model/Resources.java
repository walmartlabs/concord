package com.walmartlabs.concord.project.model;

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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.sdk.Constants.Files.*;

public class Resources implements Serializable {

    public static final Resources DEFAULT = new Resources(Collections.singletonList(PROFILES_DIR_NAME),
            Collections.singletonList(PROJECT_FILES_DIR_NAME),
            Arrays.asList(DEFINITIONS_DIR_NAMES),
            null);

    private static final long serialVersionUID = 1L;

    private final List<String> profilesPaths;
    private final List<String> projectFilePaths;
    private final List<String> definitionPaths;
    private final List<String> disabledDirs;

    public Resources(List<String> profilesPaths, List<String> projectFilePaths, List<String> definitionPaths, List<String> disabledDirs) {
        this.profilesPaths = profilesPaths;
        this.projectFilePaths = projectFilePaths;
        this.definitionPaths = definitionPaths;
        this.disabledDirs = disabledDirs;
    }

    public List<String> getProfilesPaths() {
        return profilesPaths;
    }

    public List<String> getProjectFilePaths() {
        return projectFilePaths;
    }

    public List<String> getDefinitionPaths() {
        return definitionPaths;
    }

    public List<String> getDisabledDirs() {
        return disabledDirs;
    }

    @Override
    public String toString() {
        return "Resources{" +
                "profilesPaths=" + profilesPaths +
                ", projectFilePaths=" + projectFilePaths +
                ", definitionPaths=" + definitionPaths +
                ", disabledDirs=" + disabledDirs +
                '}';
    }
}
