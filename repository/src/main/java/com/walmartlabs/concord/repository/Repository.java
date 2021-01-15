package com.walmartlabs.concord.repository;

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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class Repository {

    private final Path repoPath;
    private final FetchResult fetchResult;
    private final RepositoryProvider provider;

    public Repository(Path repoPath, FetchResult fetchResult, RepositoryProvider provider) {
        this.repoPath = repoPath;
        this.provider = provider;
        this.fetchResult = fetchResult;
    }

    public Snapshot export(Path dst) throws IOException {
        return provider.export(repoPath, dst, Collections.emptyList());
    }

    public Snapshot export(Path dst, List<String> ignorePatterns) throws IOException {
        return provider.export(repoPath, dst, ignorePatterns);
    }

    public Path path() {
        return repoPath;
    }

    @Nullable
    public FetchResult fetchResult() {
        return fetchResult;
    }
}