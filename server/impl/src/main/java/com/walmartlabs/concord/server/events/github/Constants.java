package com.walmartlabs.concord.server.events.github;

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

public final class Constants {

    public static final String EVENT_SOURCE = "github";

    public static final String COMMIT_ID_KEY = "commitId";
    public static final String IGNORE_EMPTY_PUSH_KEY = "ignoreEmptyPush";
    public static final String ORGANIZATION_KEY = "organization";
    public static final String PAYLOAD_KEY = "payload";
    public static final String PROJECT_ID_KEY = "projectId";
    public static final String PULL_REQUEST_EVENT = "pull_request";
    public static final String PUSH_EVENT = "push";
    public static final String REPO_BRANCH_KEY = "branch";
    public static final String REPO_ID_KEY = "repositoryId";
    public static final String REPO_NAME_KEY = "repository";
    public static final String REPO_ENABLED_KEY = "enabled";
    public static final String SENDER_KEY = "sender";
    public static final String STATUS_KEY = "status";
    public static final String TYPE_KEY = "type";
    public static final String VERSION_KEY = "version";
    public static final String FILES_KEY = "files";
    public static final String QUERY_PARAMS_KEY = "queryParams";
    public static final String BASE_KEY = "base";
    public static final String HEAD_KEY = "head";
    public static final String REF_KEY = "ref";

    public static final String GITHUB_ORG_KEY = "githubOrg";
    public static final String GITHUB_REPO_KEY = "githubRepo";
    public static final String GITHUB_HOST_KEY = "githubHost";

    private Constants() {

    }
}
