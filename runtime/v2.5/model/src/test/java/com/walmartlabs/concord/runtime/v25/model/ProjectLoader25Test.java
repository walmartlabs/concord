package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.imports.NoopImportManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectLoader25Test {

    @TempDir
    Path workDir;

    @Test
    void loadsSortedExtraFilesBeforeTheRoot() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("a.concord.yml"), """
                configuration:
                  arguments:
                    fromA: true
                    winner: a
                flows:
                  replaced:
                    - log: a
                """);
        Files.writeString(resources.resolve("b.concord.yml"), """
                configuration:
                  arguments:
                    fromB: true
                    winner: b
                flows:
                  extra:
                    - log: b
                """);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                  arguments:
                    winner: root
                flows:
                  replaced:
                    - log: root
                """);

        var loader = new ProjectLoader25(new NoopImportManager());
        var result = loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, null);
        var definition = (Definition25) result.projectDefinition();

        assertEquals("root", definition.configuration().arguments().get("winner"));
        assertEquals(true, definition.configuration().arguments().get("fromA"));
        assertEquals(true, definition.configuration().arguments().get("fromB"));
        assertEquals("root", definition.flows().get("replaced").steps().get(0).value());
        assertTrue(definition.flows().containsKey("extra"));
    }

    @Test
    void rejectsAnExternalSymlinkedResource() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        var external = Files.createTempFile("external", ".concord.yml");
        Files.writeString(external, """
                flows:
                  external:
                    - log: external
                """);
        Files.createSymbolicLink(resources.resolve("external.concord.yml"), external);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                """);

        var loader = new ProjectLoader25(new NoopImportManager());

        var error = assertThrows(IllegalArgumentException.class, () ->
                loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, null));
        assertTrue(error.getMessage().contains("escapes the project root"));
    }

    @Test
    void loadsAnInRootResource() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("flow.concord.yml"), """
                flows:
                  resource:
                    - log: resource
                """);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                """);

        var loader = new ProjectLoader25(new NoopImportManager());
        var result = loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, null);

        assertTrue(((Definition25) result.projectDefinition()).flows().containsKey("resource"));
    }

    @Test
    void preservesAnExplicitlyEmptyConcordResourceList() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("extra.concord.yml"), """
                flows:
                  unexpected:
                    - log: extra
                """);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                resources:
                  concord: []
                """);

        var loader = new ProjectLoader25(new NoopImportManager());
        var result = loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, null);

        assertFalse(((Definition25) result.projectDefinition()).flows().containsKey("unexpected"));
    }

    @Test
    void resolvesLeadingSlashResourcesAgainstTheProjectRoot() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("extra.concord.yml"), """
                flows:
                  resource:
                    - log: resource
                """);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                resources:
                  concord: [/concord/extra.concord.yml]
                """);

        var loader = new ProjectLoader25(new NoopImportManager());
        var result = loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, null);

        assertTrue(((Definition25) result.projectDefinition()).flows().containsKey("resource"));
    }

    @Test
    void reportsImportsInAnExtraDefinition() throws Exception {
        var resources = workDir.resolve("concord");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("extra.concord.yml"), """
                imports:
                  - dir:
                      src: imported
                flows:
                  extra:
                    - log: extra
                """);
        Files.writeString(workDir.resolve("concord.yml"), """
                configuration:
                  runtime: concord-v2.5
                """);
        var diagnostics = new ArrayList<Diagnostic>();
        ProjectLoader25Listener listener = new ProjectLoader25Listener() {
            @Override
            public void onDiagnostic(Diagnostic diagnostic) {
                diagnostics.add(diagnostic);
            }
        };

        var loader = new ProjectLoader25(new NoopImportManager());
        loader.loadProject(workDir, Definition25.RUNTIME_TYPE, imports -> imports, listener);
        assertEquals(1, diagnostics.size());

        var diagnostic = diagnostics.get(0);
        assertEquals("V25_EXTRA_IMPORTS_IGNORED", diagnostic.code());
        assertEquals(Diagnostic.Severity.WARNING, diagnostic.severity());
        assertEquals("concord/extra.concord.yml", diagnostic.range().source());
        assertEquals(2, diagnostic.range().line());
        assertEquals("$.imports", diagnostic.path());
    }

    @Test
    void advertisesOnlyTheV25Runtime() {
        var loader = new ProjectLoader25(new NoopImportManager());
        assertTrue(loader.supports("concord-v2.5"));
        assertFalse(loader.supports("concord-v2"));
    }
}
