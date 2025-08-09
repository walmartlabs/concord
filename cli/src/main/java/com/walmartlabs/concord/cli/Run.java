package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.Injector;
import com.walmartlabs.concord.cli.CliConfig.CliConfigContext;
import com.walmartlabs.concord.cli.runner.*;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.FileVisitor;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.DependencyManagerConfiguration;
import com.walmartlabs.concord.dependencymanager.DependencyManagerRepositories;
import com.walmartlabs.concord.imports.*;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.model.EffectiveConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.ProjectSerializerV2;
import com.walmartlabs.concord.runtime.v2.model.Flow;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import com.walmartlabs.concord.runtime.v2.runner.InjectorFactory;
import com.walmartlabs.concord.runtime.v2.runner.Runner;
import com.walmartlabs.concord.runtime.v2.runner.guice.ProcessDependenciesModule;
import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProviders;
import com.walmartlabs.concord.runtime.v2.runner.vm.ParallelExecutionException;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.wrapper.ProcessDefinitionV2;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "run", description = "Execute flows locally. Sends the specified <workDir> as the process payload.")
public class Run implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Option(names = {"--context"}, description = "Configuration context to use")
    String context = "default";

    @Option(names = {"-e", "--extra-vars"}, description = "additional process variables")
    Map<String, Object> extraVars = new LinkedHashMap<>();

    @Option(names = {"--default-cfg"}, description = "default Concord configuration file")
    Path defaultCfg = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("defaultCfg.yml");

    @Option(names = {"--default-task-vars"}, description = "default task variables configuration file")
    Path defaultTaskVars = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("defaultTaskVars.json");

    @Option(names = {"--deps-cache-dir"}, description = "process dependencies cache dir")
    Path depsCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("depsCache");

    @Option(names = {"--repo-cache-dir"}, description = "repository cache dir")
    Path repoCacheDir = Paths.get(System.getProperty("user.home")).resolve(".concord").resolve("repoCache");

    @Option(names = {"--secret-dir"}, description = "secret store dir")
    Path secretStoreDir;

    @Option(names = {"--vault-dir"}, description = "vault dir")
    Path vaultDir;

    @Option(names = {"--vault-id"}, description = "vault id")
    String vaultId;

    @Option(names = {"--imports-source"}, description = "default imports source")
    String importsSource = "https://github.com";

    @Option(names = {"--entry-point"}, description = "entry point")
    String entryPoint = Constants.Request.DEFAULT_ENTRY_POINT_NAME;

    @Option(names = {"-p", "--profile"}, description = "active profile")
    List<String> profiles = new ArrayList<>();

    @Option(names = {"-c", "--clean"}, description = "remove the target directory before starting the process")
    boolean cleanup = false;

    @Option(names = {"-v", "--verbose"}, description = {
            "Specify multiple -v options to increase verbosity. For example, `-v -v -v` or `-vvv`",
            "-v log flow steps",
            "-vv log task input/output args",
            "-vvv runner debug logs"})
    boolean[] verbosity = new boolean[0];

    @Option(names = {"--effective-yaml"}, description = "generate the effective YAML (skips execution)")
    boolean effectiveYaml = false;

    @Option(names = {"--default-import-version"}, description = "default import version or repo branch")
    String defaultVersion = "main";

    @Option(names = {"--no-default-cfg"}, description = "Do not load default configuration (including standard dependencies)")
    boolean noDefaultCfg = false;

    @Parameters(arity = "0..1", description = "Directory with Concord files or a path to a single Concord YAML file.")
    Path sourceDir = Paths.get(System.getProperty("user.dir"));

    @Option(names = {"--dry-run"}, description = "execute process in dry-run mode?")
    boolean dryRunMode = false;

    @Override
    public Integer call() throws Exception {
        Verbosity verbosity = new Verbosity(this.verbosity);

        CliConfigContext cliConfigContext = CliConfig.load(verbosity, context,
                new CliConfig.Overrides(secretStoreDir, vaultDir, vaultId));

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
                if (verbosity.verbose()) {
                    System.out.println("Cleaning target directory");
                }
                IOUtils.deleteRecursively(targetDir);
            }

            // copy everything into target except target
            IOUtils.copy(sourceDir, targetDir, "^target$", new CopyNotifier(verbosity.verbose() ? 0 : 100), StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IllegalArgumentException("Not a directory or single Concord YAML file: " + sourceDir);
        }

        if (!noDefaultCfg) {
            copyDefaultCfg(targetDir, defaultCfg, verbosity.verbose());
        }

        DependencyManager dependencyManager = initDependencyManager();
        ImportManager importManager = new ImportManagerFactory(dependencyManager,
                new CliRepositoryExporter(repoCacheDir), Collections.emptySet())
                .create();

        ProjectLoaderV2.Result loadResult;
        try {
            loadResult = new ProjectLoaderV2(importManager)
                    .load(targetDir, new CliImportsNormalizer(importsSource, verbosity.verbose(), defaultVersion), verbosity.verbose() ? new CliImportsListener() : null);
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

        if (verbosity.verbose() && !extraVars.isEmpty()) {
            System.out.println("Additional variables: " + extraVars);
        }

        if (verbosity.verbose() && !profiles.isEmpty()) {
            System.out.println("Active profiles: " + profiles);
        }

        // "deps" are the "dependencies" of the last profile in the list of active profiles (if present)
        Map<String, Object> overlayCfg = EffectiveConfiguration.getEffectiveConfiguration(new ProcessDefinitionV2(processDefinition), profiles);
        List<String> deps = MapUtils.getList(overlayCfg, Constants.Request.DEPENDENCIES_KEY, Collections.emptyList());

        // "extraDependencies" are additive: ALL extra dependencies from ALL ACTIVE profiles are added to the list
        List<String> extraDeps = profiles.stream()
                .flatMap(profileName -> Stream.ofNullable(processDefinition.profiles().get(profileName)))
                .flatMap(profile -> profile.configuration().extraDependencies().stream())
                .toList();

        List<String> allDeps = new ArrayList<>(deps);
        allDeps.addAll(extraDeps);

        DependencyResolver resolver = new DependencyResolver(dependencyManager, verbosity.verbose());
        Collection<String> resolvedDependencies;
        try {
            resolvedDependencies = resolver.resolveDeps(allDeps);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            return -1;
        }

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .dependencies(resolvedDependencies)
                .debug(processDefinition.configuration().debug())
                .build();

        Map<String, Object> overlayArgs = MapUtils.getMap(overlayCfg, Constants.Request.ARGUMENTS_KEY, Collections.emptyMap());
        Map<String, Object> args = ConfigurationUtils.deepMerge(processDefinition.configuration().arguments(), overlayArgs, extraVars);
        args.put(Constants.Context.TX_ID_KEY, instanceId.toString());
        args.put(Constants.Context.WORK_DIR_KEY, targetDir.toAbsolutePath().toString());
        if (verbosity.verbose()) {
            dumpArguments(args);
        }

        if (effectiveYaml) {
            Map<String, Flow> flows = new HashMap<>(processDefinition.flows());
            for (String ap : profiles) {
                Profile p = processDefinition.profiles().get(ap);
                if (p != null) {
                    flows.putAll(p.flows());
                }
            }

            ProcessDefinition pd = ProcessDefinition.builder().from(processDefinition)
                    .configuration(ProcessDefinitionConfiguration.builder().from(processDefinition.configuration())
                            .arguments(args)
                            .dependencies(allDeps)
                            .build())
                    .flows(flows)
                    .imports(Imports.builder().build())
                    .profiles(Collections.emptyMap())
                    .build();

            ProjectSerializerV2 serializer = new ProjectSerializerV2();
            serializer.write(pd, System.out);
            return 0;
        }

        System.out.println(ansi().fgBrightGreen().a("Starting...").reset());

        if (dryRunMode) {
            System.out.println("Running in the dry-run mode.");
        }

        ProcessConfiguration cfg = from(processDefinition.configuration(), processInfo(args, profiles), projectInfo(args))
                .entryPoint(entryPoint)
                .instanceId(instanceId)
                .dryRun(dryRunMode)
                .build();

        Injector injector = new InjectorFactory(new WorkingDirectory(targetDir),
                runnerCfg,
                () -> cfg,
                new ProcessDependenciesModule(targetDir, runnerCfg.dependencies(), cfg.debug()),
                new CliServicesModule(cliConfigContext, targetDir, defaultTaskVars, dependencyManager, verbosity))
                .create();

        // Just to notify listeners
        ProjectLoaderV2 loader = new ProjectLoaderV2(new NoopImportManager());
        loader.load(targetDir, new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);

        Runner runner = injector.getInstance(Runner.class);

        if (cfg.debug()) {
            System.out.println("Available tasks: " + injector.getInstance(TaskProviders.class).names());
        }

        try {
            runner.start(cfg, processDefinition, args);
        } catch (ParallelExecutionException | UserDefinedException e) {
            return -1;
        } catch (Exception e) {
            logException(verbosity, e);
            return 1;
        }

        System.out.println(ansi().fgBrightGreen().a("...done!").reset());

        return 0;
    }

    @SuppressWarnings("unchecked")
    private static ProcessInfo processInfo(Map<String, Object> args, List<String> profiles) {
        Object processInfoObject = args.get("processInfo");
        if (processInfoObject == null) {
            processInfoObject = fromExtraVars("processInfo", args);
        }

        Map<String, Object> processInfo = Collections.emptyMap();
        if (processInfoObject instanceof Map) {
            processInfo = (Map<String, Object>) processInfoObject;
        }

        return ProcessInfo.builder()
                .sessionToken(MapUtils.getString(processInfo, "sessionToken", "<undefined>"))
                .activeProfiles(profiles)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ProjectInfo projectInfo(Map<String, Object> args) {
        Object projectInfoObject = args.get("projectInfo");
        if (projectInfoObject == null) {
            projectInfoObject = fromExtraVars("projectInfo", args);
        }

        Map<String, Object> projectInfo = Collections.emptyMap();
        if (projectInfoObject instanceof Map) {
            projectInfo = (Map<String, Object>) projectInfoObject;
        }

        return ProjectInfo.builder()
                .orgName(MapUtils.getString(projectInfo, "orgName"))
                .projectName(MapUtils.getString(projectInfo, "projectName"))
                .repoName(MapUtils.getString(projectInfo, "repoName"))
                .repoUrl(MapUtils.getString(projectInfo, "repoUrl"))
                .repoBranch(MapUtils.getString(projectInfo, "repoBranch"))
                .repoPath(MapUtils.getString(projectInfo, "repoPath"))
                .repoCommitId(MapUtils.getString(projectInfo, "repoCommitId"))
                .repoCommitAuthor(MapUtils.getString(projectInfo, "repoCommitAuthor"))
                .repoCommitMessage(MapUtils.getString(projectInfo, "repoCommitMessage"))
                .build();
    }

    private static ImmutableProcessConfiguration.Builder from(ProcessDefinitionConfiguration cfg, ProcessInfo processInfo, ProjectInfo projectInfo) {
        return ProcessConfiguration.builder()
                .debug(cfg.debug())
                .entryPoint(cfg.entryPoint())
                .arguments(cfg.arguments())
                .meta(cfg.meta())
                .events(cfg.events())
                .processInfo(processInfo)
                .projectInfo(projectInfo)
                .out(cfg.out());
    }

    private static Map<String, Object> fromExtraVars(String key, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>();
        for (String k : args.keySet()) {
            if (k.startsWith(key + ".")) {
                result.put(k.substring(key.length() + 1), args.get(k));
            }
        }
        return result;
    }

    private static void copyDefaultCfg(Path targetDir, Path defaultCfg, boolean verbose) throws IOException {
        final Path destDir = targetDir.resolve("concord");
        final Path destFile = destDir.resolve("_defaultCfg.concord.yml");

        // Don't overwrite existing file is given project dir
        if (Files.exists(destFile)) {
            if (verbose) {
                System.out.println("Default configuration already exists: " + defaultCfg);
            }
            return;
        }

        if (!Files.exists(destDir)) {
            Files.createDirectory(destDir);
        }

        if (Files.exists(defaultCfg)) {
            if (Files.isRegularFile(defaultCfg)) {
                Files.copy(defaultCfg, destFile);
            } else {
                System.err.println("Default configuration must be a file!");
            }
        } else {
            try (InputStream in = Run.class.getClassLoader().getResourceAsStream("defaultCfg.yml")) {
                if (in == null) {
                    throw new IllegalStateException("Failed to load embedded default concord configuration.");
                }
                Files.copy(in, destFile);
            }
        }
    }

    private DependencyManager initDependencyManager() throws IOException {
        return new DependencyManager(getDependencyManagerConfiguration());
    }

    private DependencyManagerConfiguration getDependencyManagerConfiguration() {
        Path cfgFile = Paths.get(System.getProperty("user.home"), ".concord", "mvn.json");
        if (Files.exists(cfgFile)) {
            return DependencyManagerConfiguration.of(depsCacheDir, DependencyManagerRepositories.get(cfgFile));
        }
        return DependencyManagerConfiguration.of(depsCacheDir);
    }


    private static void dumpArguments(Map<String, Object> args) {
        ObjectMapper om = new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        try {
            System.out.print(ansi().fgYellow().a("\nProcess arguments:\n\t"));
            System.out.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(args).replace("\n", "\n\t"));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void logException(Verbosity verbosity, Exception e) {
        if (verbosity.verbose()) {
            System.err.print(ansi().fgBrightRed().a("Error: "));
            e.printStackTrace(System.err);
        } else {
            System.err.println("Error: " + e.getMessage()); // TODO
        }
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
                System.out.println(ansi().fgBrightBlack().a("Copying files into ./target/ directory..."));
                currentCount = -1;
                return;
            }

            currentCount++;
        }
    }
}
