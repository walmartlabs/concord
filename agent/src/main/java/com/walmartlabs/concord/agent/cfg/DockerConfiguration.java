package com.walmartlabs.concord.agent.cfg;

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

import com.typesafe.config.Config;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DockerConfiguration {

    private final String dockerHost;
    private final boolean orphanSweeperEnabled;
    private final long orphanSweeperPeriod;
    private final List<String> extraVolumes;
    private final boolean exposeDockerDaemon;

    @Inject
    public DockerConfiguration(Config cfg) {
        this.dockerHost = cfg.getString("docker.host");
        this.orphanSweeperEnabled = cfg.getBoolean("docker.orphanSweeperEnabled");
        this.orphanSweeperPeriod = cfg.getDuration("docker.orphanSweeperPeriod", TimeUnit.MILLISECONDS);
        this.extraVolumes = cfg.getStringList("docker.extraVolumes");
        this.exposeDockerDaemon = cfg.getBoolean("docker.exposeDockerDaemon");
    }

    public String getDockerHost() {
        return dockerHost;
    }

    public boolean isOrphanSweeperEnabled() {
        return orphanSweeperEnabled;
    }

    public long getOrphanSweeperPeriod() {
        return orphanSweeperPeriod;
    }

    public List<String> getExtraVolumes() {
        return extraVolumes;
    }

    public boolean exposeDockerDaemon() {
        return exposeDockerDaemon;
    }
}
