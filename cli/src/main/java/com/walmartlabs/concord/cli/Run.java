package com.walmartlabs.concord.cli;

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

import com.google.inject.Injector;
import com.walmartlabs.concord.cli.runner.DependencyResolver;
import com.walmartlabs.concord.cli.runner.DockerServiceImpl;
import com.walmartlabs.concord.cli.runner.SecretServiceImpl;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.Main;
import com.walmartlabs.concord.runtime.v2.runner.*;
import com.walmartlabs.concord.sdk.Constants;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Run the current directory as a Concord process")
public class Run implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    private boolean helpRequested = false;

    @Option(names = {"-e", "--extra-vars"}, description = "additional process variables")
    private Map<String, String> extraVars = new LinkedHashMap<>();

    @Option(names = { "--deps-cache-dir"}, description = "additional process variables")
    private Path depsCacheDir = Paths.get(System.getProperty("user.dir"));

    @Option(names = { "--secret-dir"}, description = "secret store dir")
    private Path secretStoreDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("secret");

    @Option(names = {"-v", "--verbose"}, description = "verbose output")
    private boolean verbose = false;

    @Parameters(arity = "0..1")
    private Path targetDir = Paths.get(System.getProperty("user.dir"));

    @Override
    public Integer call() throws Exception {
        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Not a directory: " + targetDir);
        }

        ProjectLoaderV2.Result loadResult = new ProjectLoaderV2(new NoopImportManager())
                .load(targetDir, new NoopImportsNormalizer());
        ProcessDefinition processDefinition = loadResult.getProjectDefinition();

        UUID instanceId = UUID.randomUUID();

        System.out.println("starting process '" + instanceId + "'");
        if (!extraVars.isEmpty()) {
            System.out.println("  with additional variables: " + extraVars);
        }

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(DependencyResolver.resolve(processDefinition, depsCacheDir, verbose))
                .build();
        ProcessConfiguration cfg = processDefinition.configuration();

        ClassLoader parentClassLoader = Main.class.getClassLoader();
        Injector injector = new InjectorFactory(parentClassLoader, new WorkingDirectory(targetDir), runnerCfg,
                ServicesModule.builder()
                        .secret(new SecretServiceImpl(secretStoreDir))
                        .docker(new DockerServiceImpl())
                        .build())
                .create();

        Runner runner = new Runner.Builder(instanceId, targetDir)
                .injector(injector)
                .statusCallback(id -> {})
                .build();

        Map<String, Object> args = new LinkedHashMap<>(extraVars);
        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());
        args.put("processInfo", Collections.singletonMap("sessionKey", "none"));

        try {
            runner.start(cfg.entryPoint(), args);
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        } finally {
            StateManager.cleanupState(targetDir);
            IOUtils.deleteRecursively(targetDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME));
            IOUtils.deleteRecursively(targetDir.resolve(Constants.Files.CONCORD_TMP_DIR_NAME));
        }
    }
}
