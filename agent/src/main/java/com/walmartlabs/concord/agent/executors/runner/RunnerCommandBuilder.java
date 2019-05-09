package com.walmartlabs.concord.agent.executors.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.project.InternalConstants;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunnerCommandBuilder {

    private final ObjectMapper objectMapper;

    private String javaCmd;
    private Path workDir;
    private Path procDir;
    private String agentId;
    private String serverApiBaseUrl;
    private boolean securityManagerEnabled;
    private Path dependencies;
    private Path runnerPath;
    private boolean debug;
    private String logLevel;
    private Path extraDockerVolumesFile;

    public RunnerCommandBuilder() {
        this.objectMapper = new ObjectMapper();
    }

    public RunnerCommandBuilder javaCmd(String javaCmd) {
        this.javaCmd = javaCmd;
        return this;
    }

    public RunnerCommandBuilder workDir(Path workDir) {
        this.workDir = workDir;
        return this;
    }

    public RunnerCommandBuilder procDir(Path procDir) {
        this.procDir = procDir;
        return this;
    }

    public RunnerCommandBuilder agentId(String agentId) {
        this.agentId = agentId;
        return this;
    }

    public RunnerCommandBuilder serverApiBaseUrl(String serverApiBaseUrl) {
        this.serverApiBaseUrl = serverApiBaseUrl;
        return this;
    }

    public RunnerCommandBuilder securityManagerEnabled(boolean securityManagerEnabled) {
        this.securityManagerEnabled = securityManagerEnabled;
        return this;
    }

    public RunnerCommandBuilder dependencies(Path dependencies) {
        this.dependencies = dependencies;
        return this;
    }

    public RunnerCommandBuilder runnerPath(Path runnerPath) {
        this.runnerPath = runnerPath;
        return this;
    }

    public RunnerCommandBuilder debug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public RunnerCommandBuilder logLevel(String logLevel) {
        this.logLevel = logLevel;
        return this;
    }

    public RunnerCommandBuilder extraDockerVolumesFile(Path extraDockerVolumesFile) {
        this.extraDockerVolumesFile = extraDockerVolumesFile;
        return this;
    }

    public String[] build() {
        List<String> l = new ArrayList<>();

        l.add(javaCmd);

        // JVM arguments

        List<String> agentParams = getAgentJvmParams(workDir);
        if (agentParams != null) {
            l.addAll(agentParams);
        } else {
            // default JVM parameters
            l.add("-noverify");
            l.add("-Xmx128m");
            l.add("-Djavax.el.varArgs=true");
            l.add("-Djava.security.egd=file:/dev/./urandom");
            l.add("-Djava.net.preferIPv4Stack=true");

            // workaround for JDK-8142508
            l.add("-Dsun.zip.disableMemoryMapping=true");
        }

        // Concord properties
        l.add("-DagentId=" + agentId);
        l.add("-Dapi.baseUrl=" + serverApiBaseUrl);

        if (procDir != null) {
            l.add("-Duser.dir=" + procDir.toString());
        }

        if (debug) {
            l.add("-Ddebug=true");
        }

        if (logLevel != null) {
            l.add("-DlogLevel=" + logLevel);
        }

        if (extraDockerVolumesFile != null) {
            // TODO move into the constants in something like a runner-api module?
            l.add("-Dconcord.dockerExtraVolumes=" + extraDockerVolumesFile);
        }

        // Runner's security manager
        l.add("-Dconcord.securityManager.enabled=" + securityManagerEnabled);

        // classpath
        l.add("-cp");

        // the runner's runtime is stored somewhere in the agent's libraries
        String runner = runnerPath.toString();
        l.add(runner);

        // main class
        l.add("com.walmartlabs.concord.runner.Main");

        l.add(dependencies.toString());

        return l.toArray(new String[0]);
    }

    @SuppressWarnings("unchecked")
    private List<String> getAgentJvmParams(Path workDir) {
        Path p = workDir.resolve(InternalConstants.Agent.AGENT_PARAMS_FILE_NAME);
        if (!Files.exists(p)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            return (List<String>) m.get(InternalConstants.Agent.JVM_ARGS_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
