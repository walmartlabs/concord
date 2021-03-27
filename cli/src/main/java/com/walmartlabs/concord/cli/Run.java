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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.walmartlabs.concord.cli.runner.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.FileVisitor;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportManagerFactory;
import com.walmartlabs.concord.imports.ImportProcessingException;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.process.loader.model.ProcessDefinitionUtils;
import com.walmartlabs.concord.process.loader.v2.ProcessDefinitionV2;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.ProjectSerializerV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.sdk.ImmutableProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Run the current directory as a Concord process")
public class Run implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Option(names = {"-e", "--extra-vars"}, description = "additional process variables")
    Map<String, Object> extraVars = new LinkedHashMap<>();

    @Option(names = {"--deps-cache-dir"}, description = "process dependencies cache dir")
    Path depsCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("depsCache");

    @Option(names = {"--repo-cache-dir"}, description = "repository cache dir")
    Path repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("repoCache");

    @Option(names = {"--secret-dir"}, description = "secret store dir")
    Path secretStoreDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("secrets");

    @Option(names = {"--vault-dir"}, description = "vault dir")
    Path vaultDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("vaults");

    @Option(names = {"--vault-id"}, description = "vault id")
    String vaultId = "default";

    @Option(names = {"--imports-source"}, description = "default imports source")
    String importsSource = "https://github.com";

    @Option(names = {"--entry-point"}, description = "entry point")
    String entryPoint = Constants.Request.DEFAULT_ENTRY_POINT_NAME;

    @Option(names = {"-p", "--profile"}, description = "active profile")
    List<String> profiles = new ArrayList<>();

    @Option(names = {"-c", "--clean"}, description = "remove the target directory before starting the process")
    boolean cleanup = false;

    @Option(names = {"-v", "--verbose"}, description = "verbose output")
    boolean verbose = false;

    @Option(names = {"-effective-yaml"}, description = "generate effective yaml")
    boolean effectiveYaml = false;

    @Option(names = {"--default-import-version"}, description = "default import version or repo branch")
    String defaultVersion = "main";

    @Parameters(arity = "0..1", description = "Directory with Concord files or a path to a single Concord YAML file.")
    Path sourceDir = Paths.get(System.getProperty("user.dir"));

    @Override
    public Integer call() throws Exception {
        sourceDir = sourceDir.normalize().toAbsolutePath();
        Path targetDir;

        if (Files.isRegularFile(sourceDir)) {
            Path src = sourceDir.toAbsolutePath();
            System.out.println("Running a single Concord file: " + src);

            targetDir = Files.createTempDirectory("payload");

            Files.copy(src, targetDir.resolve("concord.yml"), StandardCopyOption.REPLACE_EXISTING);
        } else if (Files.isDirectory(sourceDir)) {
            targetDir = sourceDir.resolve("target");
            if (cleanup && Files.exists(targetDir)) {
                if (verbose) {
                    System.out.println("Cleaning target directory");
                }
                IOUtils.deleteRecursively(targetDir);
            }

            // copy everything into target except target
            IOUtils.copy(sourceDir, targetDir, "^target$", new CopyNotifier(verbose ? 0 : 100), StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IllegalArgumentException("Not a directory or single Concord YAML file: " + sourceDir);
        }

        DependencyManager dependencyManager = initDependencyManager();
        ImportManager importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2.Result loadResult;
        try {
            loadResult = new ProjectLoaderV2(importManager)
                    .load(targetDir, new CliImportsNormalizer(importsSource, verbose, defaultVersion), verbose ? new CliImportsListener() : null);
        } catch (ImportProcessingException e) {
            ObjectMapper om = new ObjectMapper();
            System.err.println("Error while processing import " + om.writeValueAsString(e.getImport()) + ": " + e.getMessage());
            return -1;
        } catch (Exception e) {
            System.err.println("Error while loading " + targetDir);
            e.printStackTrace();
            return -1;
        }

        ProcessDefinition processDefinition = loadResult.getProjectDefinition();

        UUID instanceId = UUID.randomUUID();

        if (verbose && !extraVars.isEmpty()) {
            System.out.println("Additional variables: " + extraVars);
        }

        if (verbose && !profiles.isEmpty()) {
            System.out.println("Active profiles: " + profiles);
        }

        ProcessConfiguration cfg = from(processDefinition.configuration())
                .entryPoint(entryPoint)
                .instanceId(instanceId)
                .build();

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(new DependencyResolver(dependencyManager, verbose).resolveDeps(processDefinition))
                .debug(cfg.debug())
                .build();

        Map<String, Object> profileArgs = getProfilesArguments(processDefinition, profiles);
        Map<String, Object> args = ConfigurationUtils.deepMerge(cfg.arguments(), profileArgs, extraVars);
        if (verbose) {
            System.out.println("Process arguments: " + args);
        }
        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());

        if (effectiveYaml) {
            Map<String, List<Step>> flows = new HashMap<>(processDefinition.flows());
            for (String ap : profiles) {
                Profile p = processDefinition.profiles().get(ap);
                if (p != null) {
                    flows.putAll(p.flows());
                }
            }

            ProcessDefinition pd = ProcessDefinition.builder().from(processDefinition)
                    .configuration(ProcessDefinitionConfiguration.builder().from(processDefinition.configuration())
                            .arguments(args)
                            .build())
                    .flows(flows)
                    .imports(Imports.builder().build())
                    .profiles(Collections.emptyMap())
                    .build();

            ProjectSerializerV2 serializer = new ProjectSerializerV2();
            serializer.write(pd, System.out);
            return 0;
        }

        System.out.println("Starting...");

        Injector injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies()),
                new CliServicesModule(secretStoreDir, targetDir, new VaultProvider(vaultDir, vaultId)))
                .create();

        Runner runner = injector.getInstance(Runner.class);

        if (cfg.debug()) {
            System.out.println("Available tasks: " + injector.getInstance(TaskProviders.class).names());
        }

        try {
            runner.start(cfg, processDefinition, args);
        } catch (Exception e) {
            if (verbose) {
                System.err.print("Error: ");
                e.printStackTrace(System.err);
            } else {
                System.err.println("Error: " + e.getMessage());
            }
            return 1;
        }

        System.out.println("...done!");

        return 0;
    }

    private static ImmutableProcessConfiguration.Builder from(ProcessDefinitionConfiguration cfg) {
        return ProcessConfiguration.builder()
                .debug(cfg.debug())
                .entryPoint(cfg.entryPoint())
                .arguments(cfg.arguments())
                .meta(cfg.meta())
                .events(cfg.events())
                .out(cfg.out());
    }

    private static Map<String, Object> getProfilesArguments(ProcessDefinition processDefinition, List<String> profiles) {
        Map<String, Object> result = ProcessDefinitionUtils.getVariables(new ProcessDefinitionV2(processDefinition), profiles);
        return MapUtils.getMap(result, Constants.Request.ARGUMENTS_KEY, Collections.emptyMap());
    }

    private DependencyManager initDependencyManager() throws IOException {
        Path cfgFile = Paths.get(System.getProperty("user.home"), ".concord", "mvn.json");
        if (!Files.exists(cfgFile)) {
            return new DependencyManager(depsCacheDir);
        }
        return new DependencyManager(depsCacheDir, cfgFile);
    }

    private static class CopyNotifier implements FileVisitor {

        private final long notifyOnCount;

        private long currentCount = 0;

        public CopyNotifier(long notifyOnCount) {
            this.notifyOnCount = notifyOnCount;
        }

        @Override
        public void visit(Path sourceFile, Path dstFile) {
            if (currentCount == -1) {
                return;
            }

            if (currentCount == notifyOnCount) {
                System.out.println("Copying files into the target directory...");
                currentCount = -1;
                return;
            }

            currentCount++;
        }
    }
}
