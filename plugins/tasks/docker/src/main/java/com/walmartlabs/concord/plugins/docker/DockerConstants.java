package com.walmartlabs.concord.plugins.docker;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

public class DockerConstants {

    private DockerConstants() {}

    public static final int SUCCESS_EXIT_CODE = 0;
    public static final String VOLUME_CONTAINER_DEST = "/workspace";

    public static final String CMD_KEY = "cmd";
    public static final String IMAGE_KEY = "image";
    public static final String ENV_KEY = "env";
    public static final String ENV_FILE_KEY = "envFile";
    public static final String HOSTS_KEY = "hosts";
    public static final String FORCE_PULL_KEY = "forcePull";
    public static final String DEBUG_KEY = "debug";
    public static final String PULL_RETRY_COUNT_KEY = "pullRetryCount";
    public static final String PULL_RETRY_INTERVAL_KEY = "pullRetryInterval";
}
