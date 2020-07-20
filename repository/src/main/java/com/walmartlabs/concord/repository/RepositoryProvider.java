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

import com.walmartlabs.concord.sdk.Secret;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface RepositoryProvider {

    String getBranchOrDefault(String branch);

    boolean canHandle(String url);

    String fetch(String uri, String branch, String commitId, Secret secret, boolean checkRemoteCommitId, Path dst);

    Snapshot export(Path src, Path dst, List<String> ignorePatterns) throws IOException;

    RepositoryInfo getInfo(Path path);
}
