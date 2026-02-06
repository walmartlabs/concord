package com.walmartlabs.concord.it.server;

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

public final class ITConstants {

    public static final long DEFAULT_TEST_TIMEOUT = 180000;

    public static final String PROJECT_VERSION = System.getProperty("project.version", "LATEST");
    public static final String DEPENDENCIES_DIR = System.getProperty("deps.dir");
    public static final String DOCKER_ANSIBLE_IMAGE = System.getProperty("docker.ansible.image", "walmartlabs/concord-ansible");

    /**
     * Server URL accessible from within the Docker network (agent, runner, etc.).
     */
    public static final String INTERNAL_SERVER_URL = "http://server:8001";

    private ITConstants() {
    }
}
