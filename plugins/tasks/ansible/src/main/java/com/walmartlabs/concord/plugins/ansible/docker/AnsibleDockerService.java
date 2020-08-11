package com.walmartlabs.concord.plugins.ansible.docker;

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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Common interface for v1 and v2 DockerService implementations.
 */
public interface AnsibleDockerService {

    int start(DockerContainerSpec spec) throws Exception;

    class DockerContainerSpec {

        private String image;
        private List<String> args;
        private Map<String, String> env;
        private boolean debug;
        private boolean forcePull;
        private Collection<String> extraDockerHosts;
        private int pullRetryCount;
        private long pullRetryInterval;

        public String image() {
            return image;
        }

        public DockerContainerSpec image(String image) {
            this.image = image;
            return this;
        }

        public List<String> args() {
            return args;
        }

        public DockerContainerSpec args(List<String> args) {
            this.args = args;
            return this;
        }

        public Map<String, String> env() {
            return this.env;
        }

        public DockerContainerSpec env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public boolean debug() {
            return this.debug;
        }

        public DockerContainerSpec debug(boolean debug) {
            this.debug = debug;
            return this;
        }

        public boolean forcePull() {
            return this.forcePull;
        }

        public DockerContainerSpec forcePull(boolean forcePull) {
            this.forcePull = forcePull;
            return this;
        }

        public Collection<String> extraDockerHosts() {
            return this.extraDockerHosts;
        }

        public DockerContainerSpec extraDockerHosts(Collection<String> extraDockerHosts) {
            this.extraDockerHosts = extraDockerHosts;
            return this;
        }

        public int pullRetryCount() {
            return this.pullRetryCount;
        }

        public DockerContainerSpec pullRetryCount(int pullRetryCount) {
            this.pullRetryCount = pullRetryCount;
            return this;
        }

        public long pullRetryInterval() {
            return this.pullRetryInterval;
        }

        public DockerContainerSpec pullRetryInterval(long pullRetryInterval) {
            this.pullRetryInterval = pullRetryInterval;
            return this;
        }
    }
}
