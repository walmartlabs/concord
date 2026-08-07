package com.walmartlabs.concord.runtime.v25.runner.task;

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

import com.walmartlabs.concord.runtime.v25.model.parser.DefinitionParser;
import com.walmartlabs.concord.runtime.v25.runner.expression.ExpressionService;
import com.walmartlabs.concord.runtime.v25.runner.plan.PlanCompiler;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SdkContextTest {

    @TempDir
    Path workingDirectory;

    @Test
    void usesTaskEnvironmentFileServiceInTheConventionalTemporaryDirectory() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        value: true
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var environment = TaskEnvironment.local(workingDirectory);
        var scope = Scope.root(Map.of(), Set.of("default"), "default", false, false, plan);
        var context = new SdkContext(expressions, scope, plan, plan.flow("default").instructions().getFirst(),
                "test", environment, ignored -> {
                });

        assertSame(environment.fileService(), context.fileService());
        assertEquals(workingDirectory.resolve(".concord").resolve("tmp"),
                context.fileService().createTempFile("sdk-context", ".tmp").getParent());
    }

    @Test
    void normalizesRelativeTaskWorkingDirectoriesToAbsolutePaths() {
        var relative = Path.of("target", "relative-working-directory");
        var environment = TaskEnvironment.local(relative);

        assertEquals(relative.toAbsolutePath().normalize(), environment.workingDirectory());
    }

    @Test
    void exposesLazySdkStateWithoutEnablingSvmExecution() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        value: true
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var context = new SdkContext(expressions,
                Scope.root(Map.of(), Set.of("default"), "default", false, false, plan),
                plan, plan.flow("default").instructions().getFirst(), "test", TaskEnvironment.local(workingDirectory),
                ignored -> {
                });

        var execution = context.execution();
        assertSame(execution.state(), context.execution().state());
        assertEquals(0L, execution.currentThreadId().id());
        assertThrows(UnsupportedOperationException.class, execution.state()::nextThreadId);
        assertThrows(UnsupportedOperationException.class,
                () -> execution.runtime().eval(execution.state(), execution.currentThreadId()));
    }

    @Test
    void derivesCorrelationIdFromTheTaskEventRoute() throws Exception {
        var expressions = new ExpressionService();
        var definition = new DefinitionParser().parse("concord.yml", new ByteArrayInputStream("""
                configuration:
                  runtime: concord-v2.5
                flows:
                  default:
                    - set:
                        value: true
                """.getBytes(StandardCharsets.UTF_8)));
        var plan = new PlanCompiler(expressions).compile(definition);
        var instruction = plan.flow("default").instructions().getFirst();
        var step = new TaskRuntime.StepContext(instruction.path(), plan.id() + ":" + instruction.id(),
                "concord.yml", 1, 1, Map.of("loopItemIndex", 4, "retryAttempt", 2), null);
        var context = new SdkContext(expressions,
                Scope.root(Map.of(), Set.of("default"), "default", false, false, plan),
                plan, instruction, "test", step, TaskEnvironment.local(workingDirectory), null, ignored -> {
                });

        var expected = UUID.nameUUIDFromBytes((plan.id() + ":" + instruction.id() + ":4:2")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, context.execution().correlationId());
    }
}
