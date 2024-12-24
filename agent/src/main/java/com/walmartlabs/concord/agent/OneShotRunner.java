package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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
import com.google.inject.Injector;
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.guice.WorkerModule;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.nio.file.Paths;

import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.CANCELLED;
import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FAILED;
import static java.util.Objects.requireNonNull;

public class OneShotRunner {

    private static final Logger log = LoggerFactory.getLogger(OneShotRunner.class);

    private final AgentConfiguration agentCfg;
    private final ObjectMapper objectMapper;
    private final Injector injector;

    @Inject
    public OneShotRunner(AgentConfiguration agentCfg,
                         ObjectMapper objectMapper,
                         Injector injector) {

        this.agentCfg = requireNonNull(agentCfg);
        this.objectMapper = requireNonNull(objectMapper);
        this.injector = requireNonNull(injector);
    }

    public void run(String processResponseJson) throws Exception {
        var processResponse = objectMapper.readValue(processResponseJson, ProcessResponse.class);
        log.info("run [{}] -> preparing...", processResponse.getProcessId());

        var workDir = Paths.get(System.getProperty("user.dir"));
        var jobRequest = JobRequest.from(processResponse, workDir);

        var instanceId = jobRequest.getInstanceId();

        var workerModule = new WorkerModule(agentCfg.getAgentId(), instanceId, jobRequest.getSessionToken());
        var workerFactory = injector.createChildInjector(workerModule).getInstance(WorkerFactory.class);
        var worker = workerFactory.create(jobRequest, status -> {
            log.info("run ['{}'] -> {}", instanceId, status);
            var exitCode = 0;
            if (status == FAILED || status == CANCELLED) {
                exitCode = 1;
            }
            System.exit(exitCode);
        });

        worker.run();
    }
}
