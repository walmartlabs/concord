package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.walmartlabs.concord.runtime.v2.runner.TestRuntimeV2.*;
import static java.util.regex.Pattern.quote;
import static org.junit.jupiter.api.Assertions.fail;

public class LogExceptionsTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    public void shouldLogExceptionStackTraceWhenTaskThrowsException() throws Exception {
        runtime.deploy("logExceptionTests/fromTask");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. boom!") + ".*");
        // stacktrace
        assertLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$FaultyTask2.execute") + ".*");
    }

    @Test
    public void shouldLogExceptionStackTraceWhenExpressionThrowsException() throws Exception {
        runtime.deploy("logExceptionTests/fromExpression");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        // TODO: "javax.el.ELException: java.lang.Exception: ..." remove javax.el.ELException?
        assertLog(runtime.lastLog(), ".*" + quote("Error @ line: 3, col: 7. while evaluating expression '${faultyTask.exception('BOOM')}': BOOM") + ".*");
        // stacktrace
        assertLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$FaultyTask") + ".*");
    }

    @Test
    @IgnoreSerializationAssert
    public void shouldLogExceptionStackTraceWhenTaskThrowsExceptionFromParallel() throws Exception {
        runtime.deploy("logExceptionTests/fromParallel");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 4, col: 11. boom!") + ".*");

        // stacktrace
        assertLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$FaultyTask3.execute") + ".*");
    }

    @Test
    public void noStacktraceForUserDefinedExceptionFromTask() throws Exception {
        runtime.deploy("logExceptionTests/userDefinedExceptionFromTask");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 9. boom!") + ".*");

        // no single exception message
        assertNoLog(runtime.lastLog(), quote("boom!"));

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*noStacktraceForUserDefinedExceptionFromTask.*");
    }

    @Test
    public void noStacktraceForUserDefinedExceptionFromExpression() throws Exception {
        runtime.deploy("logExceptionTests/userDefinedExceptionFromExpression");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. BOOM") + ".*");

        // no single exception message
        assertNoLog(runtime.lastLog(), quote("BOOM"));

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*noStacktraceForUserDefinedExceptionFromExpression.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void noStacktraceForUserDefinedExceptionFromTaskParallel() throws Exception {
        runtime.deploy("logExceptionTests/userDefinedExceptionFromTaskParallel");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 4, col: 11. boom!") + ".*");
        assertMultiLineLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. Parallel execution errors: ") + "\n" + quote("(concord.yaml): Error @ line: 4, col: 11, thread: 1: boom!"));

        assertLogExactMatch(runtime.lastLog(), 1, ".*" + quote("Parallel execution errors") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*" + quote("com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$UserDefinedExceptionTask.execute") + ".*");
    }

    @Test
    public void noStacktraceForTaskFailReturn() throws Exception {
        runtime.deploy("logExceptionTests/failResultFromTask");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 9. boom!") + ".*");

        assertLogExactMatch(runtime.lastLog(), 1, ".*" + quote("boom!") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*noStacktraceForTaskFailReturn.*");
        assertNoLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$FaultyTask.execute") + ".*");
    }

    @Test
    public void noStackTraceForMethodNotFound() throws Exception {
        runtime.deploy("logExceptionTests/methodNotFound");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${faultyTask.unknownMethod('BOOM')}': Can't find 'unknownMethod()' method in com.walmartlabs.concord.runtime.v2.runner.tasks.Tasks$FaultyTask") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.el.LazyExpressionEvaluator.evalExpr") + ".*");
    }

    @Test
    public void noStackTraceForVariableNotFound() throws Exception {
        runtime.deploy("logExceptionTests/variableNotFound");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. while evaluating expression '${unknown}': Can't find a variable 'unknown'. Check if it is defined in the current scope. Details: ELResolver cannot handle a null base Object with identifier 'unknown'") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.el.LazyExpressionEvaluator.evalExpr") + ".*");
    }

    @Test
    public void noStacktraceForScriptException() throws Exception {
        runtime.deploy("logExceptionTests/fromScript");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLog(runtime.lastLog(), ".*" + quote("(concord.yaml): Error @ line: 3, col: 7. Something went wrong") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*DefaultScriptEvaluator.*");
    }

    @Test
    @IgnoreSerializationAssert
    public void noStacktraceForUserDefinedExceptionFromTaskParallelParallel() throws Exception {
        runtime.deploy("logExceptionTests/fromParallelParallel");

        runtime.save(ProcessConfiguration.builder()
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        // error
        assertLogExactMatch(runtime.lastLog(), 2, ".*" + quote("(concord.yaml): Error @ line: 11, col: 11. boom!") + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*" + quote("com.walmartlabs.concord.svm.ParallelExecutionException") + ".*");
        assertNoLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2.runner.vm.JoinCommand.execute") + ".*");
    }


    @Test
    public void noStackTraceForArgsEvalError() throws Exception {
        runtime.deploy("logExceptionTests/invalidArgs");

        runtime.save(ProcessConfiguration.builder()
                .putArguments("name", "${undefinedVariableName}")
                .build());

        try {
            runtime.run();
            fail("Exception expected");
        } catch (Exception e) {
            // ignore
        }

        var errorMessage = "[ERROR] Error while evaluating process arguments: while evaluating expression " +
                "'${undefinedVariableName}': Can't find a variable 'undefinedVariableName'. " +
                "Check if it is defined in the current scope. " +
                "Details: ELResolver cannot handle a null base Object with identifier 'undefinedVariableName'";

        // error
        assertLog(runtime.lastLog(), ".*" + quote(errorMessage) + ".*");

        // no stacktrace
        assertNoLog(runtime.lastLog(), ".*" + quote("at com.walmartlabs.concord.runtime.v2") + ".*");
    }
}
