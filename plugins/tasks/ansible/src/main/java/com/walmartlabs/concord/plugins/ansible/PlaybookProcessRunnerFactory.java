package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.plugins.ansible.docker.AnsibleDockerService;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import static com.walmartlabs.concord.sdk.MapUtils.*;

public final class PlaybookProcessRunnerFactory {

    private static final Logger log = LoggerFactory.getLogger(PlaybookProcessRunnerFactory.class);

    private final AnsibleDockerService dockerService;
    private final Path workDir;

    public PlaybookProcessRunnerFactory(AnsibleDockerService dockerService, Path workDir) {
        this.dockerService = dockerService;
        this.workDir = workDir;
    }

    public PlaybookProcessRunner create(Map<String, Object> args) {
        String dockerImage = getString(args, TaskParams.DOCKER_IMAGE_KEY.getKey());
        if (dockerImage != null) {
            log.info("Using the docker image: {}", dockerImage);

            Collection<String> extraHosts = DockerExtraHosts.getHosts(getMap(args, TaskParams.DOCKER_OPTS_KEY, null));
            log.info("Using extra /etc/hosts records: {}", extraHosts);

            int pullRetryCount = MapUtils.getInt(args, TaskParams.DOCKER_PULL_RETRY_COUNT.getKey(), 0);
            long pullRetryInterval = MapUtils.getInt(args, TaskParams.DOCKER_PULL_RETRY_INTERVAL.getKey(), 0);

            return new DockerPlaybookProcessRunner(dockerService, dockerImage)
                            .withForcePull(getBoolean(args, TaskParams.FORCE_PULL_KEY, true))
                            .withPullRetryCount(pullRetryCount)
                            .withPullRetryInterval(pullRetryInterval)
                            .withHosts(extraHosts);
        } else {
            return new DefaultPlaybookProcessRunner(workDir);
        }
    }
}
