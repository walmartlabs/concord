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

import com.walmartlabs.concord.cli.lint.*;
import com.walmartlabs.concord.cli.lint.LintResult.Type;
import com.walmartlabs.concord.cli.runner.CliImportsListener;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.process.loader.DelegatingProjectLoader;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.model.SourceMap;
import com.walmartlabs.concord.runtime.v1.ProjectLoaderV1;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v25.model.Definition25;
import com.walmartlabs.concord.runtime.v25.model.Diagnostic;
import com.walmartlabs.concord.runtime.v25.model.ModelException;
import com.walmartlabs.concord.runtime.v25.model.PlanValidator25;
import com.walmartlabs.concord.runtime.v25.model.ProjectLoader25;
import com.walmartlabs.concord.runtime.v25.model.ProjectLoader25Listener;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "lint", description = "Parse and validate Concord YAML files")
public class Lint implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    boolean verbose = false;

    @Parameters(arity = "0..1")
    Path targetDir = Paths.get(System.getProperty("user.dir"));

    @Override
    public Integer call() throws Exception {
        targetDir = targetDir.normalize().toAbsolutePath();

        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("Not a directory: " + targetDir);
        }

        ImportManager importManager = new NoopImportManager();
        ProjectLoaderV1 v1 = new ProjectLoaderV1(importManager);
        ProjectLoaderV2 v2 = new ProjectLoaderV2(importManager);
        ProjectLoader25 v25 = new ProjectLoader25(importManager);
        DelegatingProjectLoader loader = new DelegatingProjectLoader(Set.of(v1, v2, v25));
        var listener = new LintListener(verbose);
        ProcessDefinition pd;
        try {
            pd = loader.loadProject(targetDir, new DummyImportsNormalizer(), listener).projectDefinition();
            if (pd instanceof Definition25 definition) {
                new PlanValidator25().validate(definition);
            }
        } catch (ModelException e) {
            V25DiagnosticRenderer.print(e, System.err);
            return CliExitCodes.PROCESS_FAILED;
        }

        List<LintResult> lintResults = new ArrayList<>();
        listener.diagnostics().forEach(diagnostic -> lintResults.add(diagnosticResult(diagnostic)));
        linters().forEach(l -> lintResults.addAll(l.apply(pd)));

        if (!lintResults.isEmpty()) {
            print(lintResults);
            println();
        }

        println("Found:");
        println("  imports: " + pd.imports().items().size());
        println("  profiles: " + pd.profiles().size());
        println("  flows: " + pd.flows().size());
        println("  forms: " + pd.forms().size());
        println("  triggers: " + pd.triggers().size());
        println("  (not counting dynamically imported resources)");

        println();
        printStats(lintResults);

        println();
        boolean hasErrors = hasErrors(lintResults);
        if (hasErrors) {
            System.out.println(ansi().fgBrightRed().bold().a("INVALID").reset());
        } else {
            System.out.println(ansi().fgBrightGreen().bold().a("VALID").reset());
        }

        return hasErrors ? 10 : 0;
    }

    private static LintResult diagnosticResult(Diagnostic diagnostic) {
        var type = diagnostic.severity() == Diagnostic.Severity.ERROR ? Type.ERROR : Type.WARNING;
        return new LintResult(type, diagnostic.range() == null ? null : diagnostic.range().toSourceMap(),
                diagnostic.code() + ": " + diagnostic.message());
    }


    private List<Linter> linters() {
        return Arrays.asList(
                new ExpressionLinter(verbose),
                new TaskCallLinter(verbose)
        );
    }

    private void print(List<LintResult> results) {
        for (LintResult r : results) {
            Ansi msg = ansi();
            switch (r.getType()) {
                case ERROR: {
                    ansi().fgBrightRed().a("ERROR:").reset();
                    break;
                }
                case WARNING: {
                    ansi().fgBrightYellow().a("WARN:").reset();
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported result type: " + r.getType());
            }

            SourceMap sm = r.getSourceMap();
            if (sm != null) {
                msg.a("@ [").a(sm.source()).a("] line: ").a(sm.line()).a(", col: ").a(sm.column());
            }

            msg.append("\n\t").append(r.getMessage());

            println(msg);
            println("------------------------------------------------------------");
        }
    }

    private void printStats(List<LintResult> results) {
        long errors = results.stream().filter(r -> r.getType() == Type.ERROR).count();
        long warns = results.stream().filter(r -> r.getType() == Type.WARNING).count();
        println("Result: " + errors + " error(s), " + warns + " warning(s)");
    }

    private void println(Object o) {
        System.out.println(o);
    }

    private void println() {
        System.out.println();
    }

    private static boolean hasErrors(List<LintResult> results) {
        return results.stream().anyMatch(l -> l.getType() == Type.ERROR);
    }

    private static final class LintListener implements ProjectLoader25Listener {

        private final CliImportsListener importsListener;
        private final List<Diagnostic> diagnostics = new ArrayList<>();

        private LintListener(boolean verbose) {
            this.importsListener = verbose ? new CliImportsListener() : null;
        }

        @Override
        public void onStart(List<Import> items) {
            if (importsListener != null) {
                importsListener.onStart(items);
            }
        }

        @Override
        public void onEnd(List<Import> items) {
            if (importsListener != null) {
                importsListener.onEnd(items);
            }
        }

        @Override
        public void beforeImport(Import item) {
            if (importsListener != null) {
                importsListener.beforeImport(item);
            }
        }

        @Override
        public void afterImport(Import item) {
            if (importsListener != null) {
                importsListener.afterImport(item);
            }
        }

        @Override
        public void onDiagnostic(Diagnostic diagnostic) {
            diagnostics.add(diagnostic);
        }

        private List<Diagnostic> diagnostics() {
            return List.copyOf(diagnostics);
        }
    }
}
