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
import com.walmartlabs.concord.imports.NoopImportManager;
import com.walmartlabs.concord.process.loader.ProjectLoader;
import com.walmartlabs.concord.process.loader.model.ProcessDefinition;
import com.walmartlabs.concord.process.loader.model.SourceMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
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
import java.util.concurrent.Callable;

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

        ProjectLoader loader = new ProjectLoader(new NoopImportManager());
        ProcessDefinition pd = loader.loadProject(targetDir, new DummyImportsNormalizer(), verbose ? new CliImportsListener() : null).projectDefinition();

        List<LintResult> lintResults = new ArrayList<>();
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
            println("@|red,bold INVALID|@");
        } else {
            println("@|green,bold VALID|@");
        }

        return hasErrors ? 10 : 0;
    }

    private List<Linter> linters() {
        return Arrays.asList(
                new ExpressionLinter(verbose),
                new TaskCallLinter(verbose)
        );
    }

    private void print(List<LintResult> results) {
        for (LintResult r : results) {
            StringBuilder msg = new StringBuilder();
            switch (r.getType()) {
                case ERROR: {
                    msg.append("@|red ERROR:|@ ");
                    break;
                }
                case WARNING: {
                    msg.append("@|yellow WARN:|@ ");
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported result type: " + r.getType());
            }

            SourceMap sm = r.getSourceMap();
            if (sm != null) {
                msg.append("@ [").append(sm.source()).append("] line: ").append(sm.line()).append(", col: ").append(sm.column());
            }

            msg.append("\n\t").append(r.getMessage());

            println(msg.toString());
            println("------------------------------------------------------------");
        }
    }

    private void printStats(List<LintResult> results) {
        long errors = results.stream().filter(r -> r.getType() == Type.ERROR).count();
        long warns = results.stream().filter(r -> r.getType() == Type.WARNING).count();
        println("Result: " + errors + " error(s), " + warns + " warning(s)");
    }

    private void println(String s) {
        Ansi ansi = spec.commandLine()
                .getColorScheme()
                .ansi();

        System.out.println(ansi.string(s));
    }

    private void println() {
        System.out.println();
    }

    private static boolean hasErrors(List<LintResult> results) {
        return results.stream().anyMatch(l -> l.getType() == Type.ERROR);
    }
}
