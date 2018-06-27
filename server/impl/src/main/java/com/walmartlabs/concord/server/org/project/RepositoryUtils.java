package com.walmartlabs.concord.server.org.project;

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


import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import org.sonatype.siesta.ValidationErrorsException;

import java.util.Map;

public final class RepositoryUtils {

    public static RepositoryEntry assertRepository(ProjectEntry p, String repositoryName) {
        if (repositoryName == null) {
            throw new ValidationErrorsException("Invalid repository name");
        }

        Map<String, RepositoryEntry> repos = p.getRepositories();
        if (repos == null || repos.isEmpty()) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        RepositoryEntry r = repos.get(repositoryName);
        if (r == null) {
            throw new ValidationErrorsException("Repository not found: " + repositoryName);
        }

        return r;
    }

    private RepositoryUtils() {
    }
}
