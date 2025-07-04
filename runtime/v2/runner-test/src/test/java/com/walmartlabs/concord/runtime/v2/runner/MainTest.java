package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.cfg.LoggingConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.runner.tasks.ReentrantTaskExample;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.*;
import static java.util.regex.Pattern.quote;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class MainTest  {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    public void testVariablesAfterResume() throws Exception {
        deploy("variablesAfterResume");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*workDir1: " + runtime.workDir().toAbsolutePath() + ".*");
        assertLog(log, ".*workDir3: " + runtime.workDir().toAbsolutePath() + ".*");

        List<Form> forms = runtime.formService().list();
        assertEquals(1, forms.size());

        Form myForm = forms.get(0);
        assertEquals("myForm", myForm.name());

        // resume the process using the saved form

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Smith");

        Path newWorkDir = Files.createTempDirectory("test-new");
        IOUtils.copy(runtime.workDir(), newWorkDir);
        runtime.setWorkDir(newWorkDir);

        log = resume(myForm.eventName(), ProcessConfiguration.builder().arguments(Collections.singletonMap("myForm", data)).build());
        assertLog(log, ".*workDir4: " + runtime.workDir().toAbsolutePath() + ".*");
        assertLog(log, ".*workDir2: " + runtime.workDir().toAbsolutePath() + ".*");
    }

    @Test
    public void test() throws Exception {
        deploy("hello");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .putDefaultTaskVariables("testDefaults", Collections.singletonMap("a", "a-value"))
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, Concord!.*");
        assertLog(log, ".*" + Pattern.quote("defaultsMap:{a=a-value}") + ".*");
        assertLog(log, ".*k: \"value\".*");

        verify(runtime.processStatusCallback(), times(1)).onRunning(runtime.instanceId());
    }

    @Test
    public void testFlowNameVariable() throws Exception {
        deploy("doNotTouchFlowNameVariable");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*flowName in inner flow: 'This is MY variable'.*");

        verify(runtime.processStatusCallback(), times(1)).onRunning(runtime.instanceId());
    }

    @Test
    public void testStackTrace() throws Exception {
        deploy("stackTrace");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("(concord.yml) @ line: 9, col: 7, thread: 0, flow: flowB") + ".*");
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA") + ".*");
    }

    @Test
    public void testStackTrace2() throws Exception {
        deploy("stackTrace2");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        assertLog(runtime.lastLog(), ".*" + Pattern.quote("(concord.yml) @ line: 10, col: 7, thread: 1, flow: flowB") + ".*");
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("(concord.yml) @ line: 4, col: 11, thread: 1, flow: flowA") + ".*");
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("(concord.yml) @ line: 5, col: 11, thread: 2, flow: flowB") + ".*");
    }

    @Test
    public void testStackTrace3() throws Exception {
        deploy("stackTrace3");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("in flowA") + ".*");

        String expected = "Call stack:\n" +
                "(concord.yml) @ line: 13, col: 7, thread: 2, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 2, flow: flowA";

        String logString = new String(runtime.lastLog());
        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testStackTrace4() throws Exception {
        deploy("stackTrace4");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = "Call stack:\n" +
                "(concord.yml) @ line: 8, col: 7, thread: 0, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA";

        String expected1 = "Call stack:\n" +
                "(concord.yml) @ line: 16, col: 11, thread: 0, flow: flowThrow\n" +
                "(concord.yml) @ line: 8, col: 7, thread: 0, flow: flowB\n" +
                "(concord.yml) @ line: 3, col: 7, thread: 0, flow: flowA";

        String logString = new String(runtime.lastLog());
        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
        assertTrue(logString.contains(expected1), "expected log contains: " + expected1 + ", actual: " + logString);
    }

    @Test
    public void testStackTrace5() throws Exception {
        deploy("stackTrace5");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = ".*Call stack:\n" +
                "\\(concord.yml\\) @ line: 21, col: 7, thread: .*, flow: flowC\n" +
                "\\(concord.yml\\) @ line: 11, col: 11, thread: 2, flow: flowB\n" +
                "\\(concord.yml\\) @ line: 6, col: 7, thread: 0, flow: flowA\n" +
                "\\(concord.yml\\) @ line: 3, col: 7, thread: 0, flow: flow0.*";
        Pattern expectedPattern = Pattern.compile(expected, Pattern.MULTILINE|Pattern.DOTALL|Pattern.UNIX_LINES);

        String logString = new String(runtime.lastLog());
        assertTrue(expectedPattern.matcher(logString).matches(), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testStackTrace6() throws Exception {
        deploy("stackTrace6");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        assertNoLog(runtime.lastLog(), ".*" + Pattern.quote("[ERROR] Call stack:") + ".*");
    }

    @Test
    public void testStackTrace7() throws Exception {
        deploy("stackTrace7");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertNoLog(runtime.lastLog(), ".*" + Pattern.quote("[ERROR] Call stack:") + ".*");
    }

    @Test
    public void testForm() throws Exception {
        deploy("form");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*Before.*");

        List<Form> forms = runtime.formService().list();
        assertEquals(1, forms.size());

        Form myForm = forms.get(0);
        assertEquals("my Form", myForm.name());

        // resume the process using the saved form

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Smith");
        data.put("age", 33);

        log = resume(myForm.eventName(), ProcessConfiguration.builder().arguments(Collections.singletonMap("my Form", data)).build());
        assertLog(log, ".*After.*John Smith.*");
    }

    @Test
    public void testUnknownTask() throws Exception {
        deploy("unknownTask");

        save(ProcessConfiguration.builder().build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not found: 'unknown'"));
        }
    }

    @Test
    public void testTaskErrorBlock() throws Exception {
        deploy("faultyTask");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*boom!.*");
    }

    @Test
    public void testTaskErrorOut() throws Exception {
        deploy("faultyTaskOut");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*result.key: value.*");
    }

    @Test
    public void testTaskIgnoreErrors() throws Exception {
        deploy("taskIgnoreErrors");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*ok: false.*");
        assertLog(log, ".*error:.*boom!.*");
    }

    @Test
    public void testTaskIgnoreErrors2() throws Exception {
        deploy("taskIgnoreErrors2");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*ok: false.*");
        assertLog(log, ".*error:.*boom!.*");
    }

    @Test
    public void testFlowErrorBlock() throws Exception {
        deploy("faultyFlow");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*BOO.*");
    }

    @Test
    public void testTryErrorBlock() throws Exception {
        deploy("tryError");

        save(ProcessConfiguration.builder().build());

        try {
            run();
            fail("should fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("boom!"));
        }

        assertLog(runtime.lastLog(), ".*error occurred:.*boom!.*");
    }

    @Test
    public void testCheckpoints() throws Exception {
        deploy("checkpoints");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, Concord!.*");

        verify(runtime.processStatusCallback(), times(1)).onRunning(eq(runtime.instanceId()));
        verify(runtime.checkpointService(), times(1)).upload(any(), any(), eq("A"), any());
    }

    @Test
    public void testTaskResultPolicy() throws Exception {
        deploy("taskResultPolicy");

        save(ProcessConfiguration.builder().build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
            assertEquals("Found forbidden tasks", e.getMessage());
        }

        assertLog(runtime.lastLog(), ".*forbidden by the task policy.*");
    }

    @Test
    public void testTaskInputInterpolate() throws Exception {
        deploy("taskInputInterpolate");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, " + Pattern.quote("${myFavoriteExpression}") + "!.*");
    }

    @Test
    public void testIfExpression() throws Exception {
        deploy("ifExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "1")
                .build());

        byte[] log = run();
        assertLog(log, ".*it's clearly non-zero.*");

        verify(runtime.processStatusCallback(), times(1)).onRunning(eq(runtime.instanceId()));
    }

    @Test
    public void testSwitchExpressionCaseFound() throws Exception {
        deploy("switchExpressionFull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*It's red.*");
    }

    @Test
    public void testSwitchExpressionCaseNotFound() throws Exception {
        deploy("switchExpressionFull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = run();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionDefault() throws Exception {
        deploy("switchExpressionDefault");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red1")
                .build());

        byte[] log = run();
        assertLog(log, ".*I don't know what it is.*");
    }

    @Test
    public void testSwitchExpressionCaseExpression() throws Exception {
        deploy("switchExpressionCaseExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "red")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*Yes, I recognize this red.*");
    }

    @Test
    public void testSwitchExpressionCaseExpressionDefault() throws Exception {
        deploy("switchExpressionCaseExpression");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", "boo")
                .putArguments("aKnownValue", "red")
                .build());

        byte[] log = run();
        assertLog(log, ".*Nope.*");
    }

    @Test
    public void testScriptInline() throws Exception {
        deploy("scriptInline");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptAttached() throws Exception {
        deploy("scriptAttached");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 1.*");
    }

    @Test
    public void testScriptErrorBlock() throws Exception {
        deploy("scriptError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("(concord.yml): Error @ line: 3, col: 7. Error: this is an error") + ".*");
    }

    @Test
    public void testScriptVersion() throws Exception {
        deploy("scriptEsVersion");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*\"charCountProduct\":9.*");
    }

    @Test
    public void testScriptEsVersionInvalid() throws Exception {
        deploy("scriptEsVersionInvalid");

        save(ProcessConfiguration.builder()
                .putArguments("kv", Collections.singletonMap("k", "v"))
                .build());

        try {
            run();
        } catch (Exception e) {
            assertLog(e.toString().getBytes(), ".*unsupported.*");
            return;
        }
        throw new Exception("invalid esVersion should have thrown");
    }


    @Test
    public void testScriptUnboundedInputMapOk() throws Exception {
        deploy("scriptUnboundedInputMapOk");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*ok.*");
    }

    @Test
    public void testScriptVariablesSanitize() throws Exception {
        deploy("scriptVariablesSanitize");

        save(ProcessConfiguration.builder()
                .putArguments("kv", Collections.singletonMap("k", "v"))
                .build());

        byte[] log = run();
        assertLog(log, ".*boom: \\{\\}.*");
    }

    @Test
    public void testScriptOut() throws Exception {
        deploy("scriptOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result.boom: \\{\\}.*");
        assertLog(log, ".*result.k1: value1");
        assertLog(log, ".*result.k2: value2");
    }

    @Test
    public void testScriptOutExpr() throws Exception {
        deploy("scriptOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*k1: value1");
    }

    @Test
    public void testNonSerializableLocal() throws Exception {
        deploy("nonSerializableLocal");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*error occurred:.*Can't set a non-serializable local variable.*");
    }

    @Test
    public void testInitiator() throws Exception {
        deploy("initiator");

        Map<String, Object> initiator = new HashMap<>();
        initiator.put("username", "test");
        initiator.put("displayName", "Test User");

        save(ProcessConfiguration.builder()
                .initiator(initiator)
                .putArguments("name", "${initiator.displayName}")
                .build());

        byte[] log = run();
        assertLog(log, ".*Test User.*");
    }

    @Test
    public void testSegmentedLogging() throws Exception {
        deploy("logging");

        save(ProcessConfiguration.builder()
                .build());

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .sendSystemOutAndErrToSLF4J(false)
                        .workDirMasking(false)
                        .build())
                .build();

        byte[] log = run(runnerCfg);
        assertLog(log, "^This goes directly into the stdout$");
        assertLog(log, ".*This is a processLog entry.*");
    }

    @Test
    public void testMultipleWithItems() throws Exception {
        deploy("multipleWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        for (int i = 1; i < 7; i++) {
            assertLog(log, ".*item: " + i + ".*");
        }
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelWithItemsTask() throws Exception {
        deploy("parallelWithItemsTask");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
        assertLog(log, ".*threadIds: \\[1, 2, 3].*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelLoopTask() throws Exception {
        deploy("parallelLoopTask");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
        assertLog(log, ".*threadIds: \\[1, 2, 3].*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelWithError() throws Exception {
        deploy("parallelWithError");

        save(ProcessConfiguration.builder()
                .build());

        Exception exception = assertThrows(Exception.class, this::run);
        assertTrue(exception.getMessage().matches("(?s)Parallel execution errors:.*boom.*\n.*boom.*"));
    }

    @Test
    public void testWithItemsBlock() throws Exception {
        deploy("withItemsBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
    }

    @Test
    public void testLoopBlock() throws Exception {
        deploy("loopBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: \\[10, 20, 30\\].*");
    }

    @Test
    public void testWithItemsSet() throws Exception {
        deploy("withItemsSet");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLogAtLeast(log, 3, ".*empty: \\[\\].*");
        assertLog(log, ".*after add: \\[1\\].*");
        assertLog(log, ".*after add: \\[2\\].*");
        assertLog(log, ".*after add: \\[3\\].*");
    }

    @Test
    public void testLoopSet() throws Exception {
        deploy("loopSet");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLogAtLeast(log, 3, ".*empty: \\[\\].*");
        assertLog(log, ".*after add: \\[1\\].*");
        assertLog(log, ".*after add: \\[2\\].*");
        assertLog(log, ".*after add: \\[3\\].*");
    }

    @Test
    public void testUnknownMethod() throws Exception {
        deploy("unknownMethod");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("should fail");
        } catch (Exception e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("Can't find 'sayGoodbye()' method"), msg);
            assertTrue(msg.contains("Did you mean: sayHello()"));
        }
    }

    @Test
    public void testSuspend() throws Exception {
        deploy("suspend");

        save(ProcessConfiguration.builder()
                .putArguments("testValue", "XYZ")
                .build());

        byte[] log = run();
        assertLog(log, ".*aaa.*");

        log = resume("ev1", ProcessConfiguration.builder().build());
        assertLog(log, ".*XYZ.*");
    }

    @Test
    public void testDefaultProcessVariables() throws Exception {
        deploy("defaultVariables");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*workDir: '.*'.*");
        assertLog(log, ".*processInfo: '\\{.*\\}'.*");
        assertLog(log, ".*projectInfo: '\\{\\}'.*");

        log = resume("ev1", ProcessConfiguration.builder()
                .putArguments("workDir", "1")
                .putArguments("processInfo", "2")
                .putArguments("projectInfo", "3")
                .build());
        assertLog(log, ".*workDir: '.*'.*");
        assertLog(log, ".*processInfo: '\\{.*\\}'.*");
        assertLog(log, ".*projectInfo: '\\{\\}'.*");
    }

    @Test
    public void testSetVariables() throws Exception {
        deploy("setVariables");

        save(ProcessConfiguration.builder()
                .putArguments("k1", "XYZ")
                .putArguments("k2", "init")
                .build());

        byte[] log = run();
        assertLog(log, ".*k1-value, init, k3-value.*");
    }

    @Test
    public void testReturn() throws Exception {
        deploy("return");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*before return.*");
        assertNoLog(log, ".*after return.*");
    }

    @Test
    public void testExit() throws Exception {
        deploy("exit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*before exit.*");
        assertNoLog(log, ".*after exit.*");
    }

    @Test
    public void testCallFlow() throws Exception {
        deploy("call");

        save(ProcessConfiguration.builder()
                .putArguments("flowName", "myFlow")
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("Hello, default flow!") + ".*");
        assertLog(log, ".*" + Pattern.quote("Hello, myFlow flow!") + ".*");
    }

    @Test
    public void testCallFlowWithErrorBlock() throws Exception {
        deploy("callWithErrorBlock");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*variable has been planted.*");
        assertLog(log, ".*myVar should exist: world.*");
    }

    @Test
    public void testVariableVisibilityAfterErrorBlocks() throws Exception {
        deploy("errorBlockScoping");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, world!.*");
    }

    @Test
    public void testCallFlowOut() throws Exception {
        deploy("callOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out a=a-value") + ".*");
        assertLog(log, ".*" + quote("array out a=a-value, b=b-value") + ".*");
        assertLog(log, ".*" + quote("expression out a=a-value, xx=123, zz=10000") + ".*");
        assertLog(log, ".*" + quote("out after suspend: a=aaa-value") + ".*");

        verify(runtime.checkpointService(), times(1)).upload(any(), any(), eq("A"), any());
    }

    @Test
    public void testCallOutWithItems() throws Exception {
        deploy("callOutWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("array out: x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("expression out: x=[10, 20, 30]") + ".*");
        assertLog(log, ".*" + quote("expression out: xx=[100, 200, 300]") + ".*");
    }

    @Test
    public void testVarScoping() throws Exception {
        deploy("varScoping");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .build());

        byte[] log = run();
        assertLog(log, ".*1: 123.*");
        assertLog(log, ".*a: 123.*");
        assertLog(log, ".*2: false.*");
        assertLog(log, ".*3: 345.*");
        assertLog(log, ".*4: 456.*");
        assertLog(log, ".*c: 456.*");
        assertLog(log, ".*5: 567.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelIn() throws Exception {
        deploy("parallelIn");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .putArguments("y", 234)
                .build());

        byte[] log = run();
        assertLog(log, ".*thread A, x: 123.*");
        assertLog(log, ".*thread B, y: 234.*");
        assertLog(log, ".*thread C, x: 999.*");
        assertLog(log, ".*main, x: 123.*");
        assertLog(log, ".*main, y: 234.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelOut() throws Exception {
        deploy("parallelOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 123.*");
        assertLog(log, ".*y: 234.*");
    }

    @Test
    public void testSerialLoopEmptyCall() throws Exception {
        deploy("serialEmptyCall");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("outVar: [null, null]") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelLoopEmptyCall() throws Exception {
        deploy("parallelEmptyCall");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("outVar: [null, null]") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelOutExpr() throws Exception {
        deploy("parallelOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x: 123.*");
        assertLog(log, ".*y: \\{inner=234\\}.*");
    }

    @Test
    public void testReentrant() throws Exception {
        deploy("reentrantTask");
        save(ProcessConfiguration.builder()
                .putArguments("actionName", "boo")
                .build());

        byte[] log = run();
        assertLog(log, ".*execute .*action=boo.*");

        log = resume(ReentrantTaskExample.EVENT_NAME, ProcessConfiguration.builder().build());
        assertLog(log, ".*result.ok: true.*");
        assertLog(log, ".*result.action: boo.*");
        assertLog(log, ".*result.k: v.*");
        assertLog(log, ".*resultAction: boo.*");
    }

    @Test
    public void testReentrantWithError() throws Exception {
        deploy("reentrantTaskWithError");
        save(ProcessConfiguration.builder()
                .putArguments("actionName", "boo")
                .putArguments("errorOnResume", true)
                .build());

        byte[] log = run();
        assertLog(log, ".*execute .*errorOnResume=true.*");

        log = resume(ReentrantTaskExample.EVENT_NAME, ProcessConfiguration.builder().build());
        assertLog(log, ".*error handled: java.lang.RuntimeException: Error on resume.*");
        assertLog(log, ".*process finished.*");
    }

    @Test
    public void testNestedSet() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("deep", 3);
        args.put("z", 234);

        deploy("nestedSet");
        save(ProcessConfiguration.builder()
                .putArguments("x", args)
                .build());

        byte[] log = run();
        assertLog(log, ".*x: .*y=123.*");
        assertLog(log, ".*x: .*z=234.*");
        assertLog(log, ".*x: .*taskOut=42.*");
        assertLog(log, ".*x: .*taskOut2=165.*");
        assertLog(log, ".*x: .*fromArgs=234.*");
        assertLog(log, ".*x: .*deep=\\{beep=1\\}.*");
        assertLog(log, ".*a: 42.*");
        assertLog(log, ".*a2: 1.*");
    }

    @Test
    public void testSetMapVariableOverride() throws Exception {
        deploy("setVariableOverride");
        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*myMap1: .*\\{y=2\\}.*");
        assertLog(log, ".*myMap2: .*\\{y=2, z=3\\}.*");
        assertLog(log, ".*myMap3: .*\\{z=4\\}.*");
        assertLog(log, ".*myMap4: .*\\{k=v\\}.*");
    }

    @Test
    public void testRetry() throws Exception {
        deploy("retry");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLog(log, ".*faultyOnceTask: fail.*");
        assertLog(log, ".*Waiting for 1000ms.*");
        assertLog(log, ".*faultyOnceTask: ok.*");
        assertLog(log, ".*neverFailTask: ok.*");
    }

    @Test
    public void testRetryInput() throws Exception {
        deploy("retryInput");

        save(ProcessConfiguration.builder().build());

        byte[] log = run();
        assertLogAtLeast(log, 1, ".*ConditionallyFailTask: fail.*");
        assertLog(log, ".*Waiting for 1000ms.*");
        assertLog(log, ".*ConditionallyFailTask: ok.*");
    }

    @Test
    public void testCheckpointExpr() throws Exception {
        deploy("checkpointExpr");

        save(ProcessConfiguration.builder()
                .putArguments("x", 123)
                .build());

        run();
        verify(runtime.checkpointService(), times(1)).upload(any(), any(), eq("test_123"), any());
    }

    @Test
    public void testCheckpointRestore() throws Exception {
        deploy("checkpointRestore");

        save(ProcessConfiguration.builder()
                .putArguments("aVar", Collections.singletonMap("x", 123))
                .build());

        run();

        verify(runtime.checkpointService(), times(1)).upload(any(), any(), eq("first"), any());
        verify(runtime.checkpointService(), times(1)).upload(any(), any(), eq("second"), any());

        runtime.checkpointService().restore("first", runtime.workDir());

        run();

        assertLogAtLeast(runtime.allLogs(), 2, ".*#3.*x=124.*");
        assertLogAtLeast(runtime.allLogs(), 2, ".*#3.*y=345.*");

        assertLog(runtime.allLogs(), ".*Event Name: first.*");
    }

    @Test
    public void testCheckpoint1_93_0Restore() throws Exception {
        deploy("checkpointRestore2");

        save(ProcessConfiguration.builder()
                .build());

        runtime.checkpointService().put("first", Paths.get(MainTest.class.getResource("checkpointRestore2/first_1.103.1.zip").toURI()));
        runtime.checkpointService().restore("first", runtime.workDir());

        run();

        assertLog(runtime.allLogs(), ".*item: one.*");
        assertLog(runtime.allLogs(), ".*item: two.*");
    }

    @Test
    public void testNullIfExpression() throws Exception {
        deploy("ifExpressionAsNull");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", Collections.singletonMap("x", 123))
                .build());

        byte[] log = run();
        assertLog(log, ".*it's null.*");
    }

    @Test
    public void testTaskOut() throws Exception {
        deploy("taskOut");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x.ok=true") + ".*");
        assertLog(log, ".*" + quote("single out x.k=some-value") + ".*");
        assertLog(log, ".*" + quote("expression out x=some-value") + ".*");
    }

    @Test
    public void testTaskOutWithItems() throws Exception {
        deploy("taskOutWithItems");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + quote("single out x=[10, 20, 30]") + ".*");
    }

    @Test
    public void testExprOutExpression() throws Exception {
        deploy("exprOutExpr");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*result: v.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testFormsParallel() throws Exception {
        deploy("parallelForm");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*Before parallel.*");

        List<Form> forms = runtime.formService().list();
        assertEquals(2, forms.size());

        Form form1 = forms.stream()
                .filter(f -> "form1".equals(f.name())).findFirst()
                .orElseThrow(() -> new RuntimeException("form not found"));

        // resume the process using the saved form

        log = resume(form1.eventName(), ProcessConfiguration.builder()
                .arguments(Collections.singletonMap("form1", Collections.singletonMap("firstName", "Vasia")))
                .build());
        assertLog(log, ".*form1 in block: Vasia.*");

        // resume the process using the saved form

        Form form2 = runtime.formService().list().stream()
                .filter(f -> "form2".equals(f.name())).findFirst()
                .orElseThrow(() -> new RuntimeException("form not found"));
        log = resume(form2.eventName(), ProcessConfiguration.builder()
                .arguments(Collections.singletonMap("form2", Collections.singletonMap("firstName", "Pupkin")))
                .build());
        assertLog(log, ".*form2: Pupkin.*");
        assertLog(log, ".*form1: Vasia.*");
    }

    @Test
    public void testContextInjector() throws Exception {
        deploy("injectorTest");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*done!.*");
    }

    @Test
    public void testContextInjectorWithSegmentedLogger() throws Exception {
        deploy("injectorTest");

        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .logging(LoggingConfiguration.builder()
                        .build())
                .build();

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run(runnerCfg);

        assertLog(log, Pattern.quote("|0|1|1|0|0||70|2|0|0|0|") + ".*done!.*");
        assertLog(log, Pattern.quote("|0|2|1|0|0|"));
    }

    @Test
    public void testCurrentFlowName() throws Exception {
        deploy("currentFlowName");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*default: default.*");
        assertLog(log, ".*myFlow: myFlow.*");

        runtime.checkpointService().restore("first", runtime.workDir());

        run();

        assertLogAtLeast(runtime.allLogs(), 2, ".*after checkpoint: default.*");
        assertLogAtLeast(runtime.allLogs(), 2, ".*current flow name in error block: default.*");
    }

    @Test
    public void testEvalAsMap() throws Exception {
        deploy("evalAsMap");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x:.*a.out3=\\$\\{a.out1}.*");
        assertLog(log, ".*x:.*a.out1=1.*");
        assertLog(log, ".*x:.*a.out2=2.*");

        assertLog(log, ".*eval: \\{a=\\{.*");
        assertLog(log, ".*1: 1.*");
        assertLog(log, ".*2: 2.*");
        assertLog(log, ".*3: 1.*");
    }

    @Test
    public void testOrDefaultFunction() throws Exception {
        deploy("orDefault");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*x:.*defaultValue.*");

        // ---
        save(ProcessConfiguration.builder()
                .putArguments("x", "x-value")
                .build());

        log = run();
        assertLog(log, ".*x:.*x-value.*");
    }

    @Test
    public void testIsDebug() throws Exception {
        deploy("isDebug");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*isDebug: false.*");

        save(ProcessConfiguration.builder()
                .debug(true)
                .build());

        log = run();
        assertLog(log, ".*isDebug: true.*");
    }

    @Test
    public void argsFromArgs() throws Exception {
        deploy("argsFromArgs");

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("k1", "v1");
        args.put("k2", "${resultTask.get('args.k1')}");

        save(ProcessConfiguration.builder()
                .putArguments("args", args)
                .build());

        byte[] log = run();
        assertLog(log, ".*Hello, " + Pattern.quote("{k1=v1, k2=v1}") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void loopItemSerialization() throws Exception {
        deploy("loopSerializationError");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*name=one.*");
    }

    @Test
    public void testThrowExpression() throws Exception {
        deploy("throwExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
            assertEquals("42 not found", e.getMessage());
        }
    }

    @Test
    public void testSensitiveData() throws Exception {
        deploy("sensitiveData");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("log value: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("hack: M A S K _ M E ") + ".*");

        assertLog(log, ".*" + Pattern.quote("map: {nonSecretButMasked=******, secret=******}") + ".*");
        assertLog(log, ".*" + Pattern.quote("map: {nonSecret=non secret value, secret=******}") + ".*");
        assertLog(log, ".*" + Pattern.quote("map.nested: {nonSecret=non secret value, secret={top-secret=******}}") + ".*");

        assertLog(log, ".*" + Pattern.quote("plain: plain") + ".*");

        assertLog(log, ".*" + Pattern.quote("secret from map: ******") + ".*");

        assertLog(log, ".*secret from task execute: .*" + Pattern.quote("keyWithSecretValue=******") + ".*");

        log = resume("ev1", ProcessConfiguration.builder().build());
        assertLog(log, ".*" + Pattern.quote("mySecret after suspend: ******") + ".*");
    }

    @Test
    public void testBse64SensitiveData() throws Exception {
        deploy("base64Sensitive");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("1. sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("1. also sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("1. non sensitive: NOT_SECRET") + ".*");

        assertLog(log, ".*" + Pattern.quote("2. base64 encode sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("2. base64 encode non sensitive: Tk9UX1NFQ1JFVA==") + ".*");

        assertLog(log, ".*" + Pattern.quote("3. base64 decode sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("3. base64 decode base64 sensitive: ******") + ".*");
        assertLog(log, ".*" + Pattern.quote("3. base64 decode non sensitive: NOT_SECRET") + ".*");
    }

    @Test
    public void testIncVariable() throws Exception {
        deploy("incVariable");

        save(ProcessConfiguration.builder()
                .putArguments("counter", 0)
                .build());

        byte[] log = run();
        assertLog(log, ".*counter: 1.*");
    }

    @Test
    public void testHasNonNullVariable() throws Exception {
        deploy("hasNonNullVariable");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*true == true.*");
        assertLog(log, ".*false == false.*");
    }

    @Test
    public void testInvalidExpressionError() throws Exception {
        deploy("invalidExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String logString = new String(runtime.lastLog());
        String expected = "[ERROR] (concord.yml): Error @ line: 9, col: 7. while parsing expression '${str.split('\\n')}': Encountered \"\\'\\\\n\" at line 1, column 13.\n" +
                "Was expecting one of:\n" +
                "    \"{\" ...\n" +
                "    <INTEGER_LITERAL> ...\n" +
                "    <FLOATING_POINT_LITERAL> ...\n" +
                "    <STRING_LITERAL> ...\n" +
                "    \"true\" ...\n" +
                "    \"false\" ...\n" +
                "    \"null\" ...\n" +
                "    \"(\" ...\n" +
                "    \")\" ...\n" +
                "    \"[\" ...\n" +
                "    \"!\" ...\n" +
                "    \"not\" ...\n" +
                "    \"empty\" ...\n" +
                "    \"-\" ...\n" +
                "    <IDENTIFIER> ...";

        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testNPEInExpression() throws Exception {
        deploy("npeInExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String logString = new String(runtime.lastLog());
        String expected = "[ERROR] (concord.yml): Error @ line: 7, col: 7. while evaluating expression '${'a' += m.n += 'b'}': ";

        assertTrue(logString.contains(expected), "expected log contains: " + expected + ", actual: " + logString);
    }

    @Test
    public void testExpressionThrowUserDefinedError() throws Exception {
        deploy("faultyExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. BOOM"));
    }

    @Test
    public void testExpressionThrowException() throws Exception {
        deploy("exceptionFromExpression");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }

        String expected = "[ERROR] (concord.yml): Error @ line: 3, col: 7. while evaluating expression '${faultyTask.exception('BOOM')}': BOOM";
        assertLog(runtime.lastLog(), ".*" + Pattern.quote(expected));
    }


    @Test
    public void testTaskThrowException() throws Exception {
        deploy("faultyTask4");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("must fail");
        } catch (Exception e) {
            // ignore
        }
        assertLog(runtime.lastLog(), ".*" + Pattern.quote("[ERROR] (concord.yml): Error @ line: 3, col: 7. boom!"));
    }

    @Test
    public void testUnresolvedVarInStepName() throws Exception {
        deploy("unresolvedVarInStepName");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(runtime.lastLog(), ".*" + quote("(concord.yml): Error @ line: 4, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'") + ".*");
    }

    @Test
    public void testUnresolvedVarInLoop() throws Exception {
        deploy("unresolvedVarInLoop");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(runtime.lastLog(), ".*" + quote("(concord.yml): Error @ line: 6, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'") + ".*");
    }

    @Test
    public void testUnresolvedVarInRetry() throws Exception {
        deploy("unresolvedVarInRetry");

        save(ProcessConfiguration.builder()
                .build());

        try {
            run();
            fail("exception expected");
        } catch (Exception e) {
        }

        assertLog(runtime.lastLog(), ".*" + quote("(concord.yml): Error @ line: 6, col: 7. while evaluating expression '${undefined}': Can't find a variable 'undefined'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'undefined'") + ".*");
    }

    @Test
    public void testArrayEvalSerialize() throws Exception {
        deploy("lazyEvalMapInArgs");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("{dev=dev-cloud1}, {prod=prod-cloud1}, {test=test-cloud1}, {perf=perf-cloud2}, {ci=perf-ci}") + ".*");
    }

    @Test
    public void testEntrySetSerialization() throws Exception {
        deploy("entrySetSerialization");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*myList: \\[k=v\\].*");
    }

    @Test
    public void testHasFlow() throws Exception {
        deploy("hasFlow");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*123: false.*");
        assertLog(log, ".*myFlow: true.*");
    }

    @Test
    public void testUuidFunc() throws Exception {
        deploy("uuid");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*uuid: [0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
    }

    @Test
    public void testExitFromParallelLoop() throws Exception {
        deploy("parallelLoopExit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();

        assertNoLog(log, ".*should not reach here.*");
    }

    @Test
    public void testExitFromSerialLoop() throws Exception {
        deploy("serialLoopExit");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();

        assertNoLog(log, ".*should not reach here.*");

        assertLog(log, ".*inner start: one.*");
        assertLog(log, ".*inner end: one.*");
        assertLog(log, ".*inner start: two.*");

        assertNoLog(log, ".*inner end: two.*");
        assertNoLog(log, ".*inner start: three.*");
        assertNoLog(log, ".*inner start: four.*");
    }

    @Test
    public void testStringIfExpression() throws Exception {
        deploy("ifExpressionAsString");

        save(ProcessConfiguration.builder()
                .putArguments("myVar", Collections.singletonMap("str", "true"))
                .build());

        byte[] log = run();
        assertLog(log, ".*it's true.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testParallelLoopItemIndex() throws Exception {
        deploy("parallelLoopItemIndex");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*serial: five==5.*");
        assertLog(log, ".*serial: four==4.*");
        assertLog(log, ".*serial: three==3.*");
        assertLog(log, ".*serial: two==2.*");
        assertLog(log, ".*serial: one==1.*");

        assertLog(log, ".*parallel: five==5.*");
        assertLog(log, ".*parallel: four==4.*");
        assertLog(log, ".*parallel: three==3.*");
        assertLog(log, ".*parallel: two==2.*");
        assertLog(log, ".*parallel: one==1.*");
    }

    @Test
    public void testDoubleValues() throws Exception {
        deploy("doubleValues");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*PI1=3.14159264.*");
        assertLog(log, ".*PI2=3.14159264.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void testThreadLocals() throws Exception {
        deploy("threadLocals");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = run();
        assertLog(log, ".*value: myValue1.*");
        assertLog(log, ".*value: myValue2.*");
        assertLog(log, ".*value: myValue3.*");
    }

    @Test
    public void dryRunReadyAsExpression() throws Exception {
        deploy("dryRunReadyAsExpression");

        save(ProcessConfiguration.builder()
                .dryRun(true)
                .build());

        byte[] log = run();
        assertLog(log, ".*myValue: 42.*");
    }

    @Test
    public void flowCallOutExpression() throws Exception {
        deploy("flowCallOutExpression");

        save(ProcessConfiguration.builder()
                .dryRun(true)
                .build());

        byte[] log = run();
        assertLog(log, ".*" + Pattern.quote("out as expression (array): abc") + ".*");
        assertLog(log, ".*" + Pattern.quote("out as expression (map): abc") + ".*");
        assertLog(log, ".*" + Pattern.quote("out as expression (array) with loop: [abc, abc]") + ".*");
        assertLog(log, ".*" + Pattern.quote("out as expression (map) with loop: [abc_0, abc_1]") + ".*");
    }

    @Test
    public void flowCallOutExpressionCompat() throws Exception {
        deploy("flowCallOutCompat");

        save(ProcessConfiguration.builder()
                .build());

        // checkpoint with state before changes
        runtime.checkpointService().put("first", Paths.get(MainTest.class.getResource("flowCallOutCompat/first.zip").toURI()));
        runtime.checkpointService().restore("first", runtime.workDir());

        run();

        assertLog(runtime.allLogs(), ".*" + Pattern.quote("out as array: abc") + ".*");
        assertLog(runtime.allLogs(), ".*" + Pattern.quote("out as map: abc") + ".*");
        assertLog(runtime.allLogs(), ".*" + Pattern.quote("out as array with loop: [abc, abc]") + ".*");
        assertLog(runtime.allLogs(), ".*" + Pattern.quote("out as map with loop: [abc_0, abc_1]") + ".*");
    }

    private void deploy(String name) throws URISyntaxException, IOException {
        runtime.deploy(name);
    }

    private void save(ProcessConfiguration cfg) {
        runtime.save(cfg);
    }

    private byte[] run() throws Exception {
        return runtime.run();
    }

    private byte[] run(RunnerConfiguration baseCfg) throws Exception {
        return runtime.run(baseCfg);
    }

    private byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        return runtime.resume(eventName, cfg);
    }
}
