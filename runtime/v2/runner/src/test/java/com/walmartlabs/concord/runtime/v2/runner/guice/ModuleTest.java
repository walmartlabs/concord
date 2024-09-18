package com.walmartlabs.concord.runtime.v2.runner.guice;

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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.PersistenceService;
import com.walmartlabs.concord.runtime.v2.runner.ProcessStatusCallback;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointService;
import com.walmartlabs.concord.runtime.v2.runner.checkpoints.CheckpointUploader;
import com.walmartlabs.concord.runtime.v2.runner.logging.LoggingClient;
import com.walmartlabs.concord.runtime.v2.runner.logging.RunnerLogger;
import com.walmartlabs.concord.runtime.v2.sdk.DependencyManager;
import com.walmartlabs.concord.runtime.v2.sdk.DockerService;
import com.walmartlabs.concord.runtime.v2.sdk.FileService;
import com.walmartlabs.concord.runtime.v2.sdk.LockService;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessInfo;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModuleTest {

    @TempDir
    private Path tempDir;

    private Validator injectValidator() {
        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .api(ApiConfiguration.builder().build())
                .build();

        ProcessConfiguration processConfiguration = ProcessConfiguration.builder()
                .processInfo(ProcessInfo.builder()
                        .sessionToken(UUID.randomUUID().toString())
                        .build())
                .build();

        Injector injector = new InjectorFactory(new WorkingDirectory(tempDir),
                runnerCfg,
                () -> processConfiguration,
                new DefaultRunnerModule(), // bind default services
                new ProcessDependenciesModule(tempDir, runnerCfg.dependencies(), runnerCfg.debug())) // grab process dependencies
                .create();

        return injector.getInstance(Validator.class);
    }

    @BeforeEach
    public void setup() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    void testDefaultProviderResults() {
        Validator validator = injectValidator();

        assertNotNull(validator);
        validator.validate();
    }

    private static class Validator {

        private final CheckpointUploader checkpointUploader;
        private final CheckpointService checkpointService;
        private final DependencyManager dependencyManager;
        private final DockerService DockerService;
        private final FileService fileService;
        private final EventReportingService eventReportingService;
        private final LockService lockService;
        private final PersistenceService persistenceService;
        private final ProcessStatusCallback processStatusCallback;
        private final SecretService secretService;
        private final RunnerLogger runnerLogger;
        private final LoggingClient loggingClient;

        @Inject
        public Validator(CheckpointUploader checkpointUploader,
                         CheckpointService checkpointService,
                         DependencyManager dependencyManager,
                         DockerService DockerService,
                         FileService fileService,
                         EventReportingService eventReportingService,
                         LockService lockService,
                         PersistenceService persistenceService,
                         ProcessStatusCallback processStatusCallback,
                         SecretService secretService,
                         RunnerLogger runnerLogger,
                         LoggingClient loggingClient) {
            this.checkpointUploader = checkpointUploader;
            this.checkpointService = checkpointService;
            this.dependencyManager = dependencyManager;
            this.DockerService = DockerService;
            this.fileService = fileService;
            this.eventReportingService = eventReportingService;
            this.lockService = lockService;
            this.persistenceService = persistenceService;
            this.processStatusCallback = processStatusCallback;
            this.secretService = secretService;
            this.runnerLogger = runnerLogger;
            this.loggingClient = loggingClient;
        }

        public void validate() {
            assertNotNull(checkpointUploader);
            assertNotNull(checkpointService);
            assertNotNull(dependencyManager);
            assertNotNull(DockerService);
            assertNotNull(fileService);
            assertNotNull(eventReportingService);
            assertNotNull(lockService);
            assertNotNull(persistenceService);
            assertNotNull(processStatusCallback);
            assertNotNull(secretService);
            assertNotNull(runnerLogger);
            assertNotNull(loggingClient);
        }
    }

}
