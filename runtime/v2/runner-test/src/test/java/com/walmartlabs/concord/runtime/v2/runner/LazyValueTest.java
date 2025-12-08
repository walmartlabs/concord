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

public class LazyValueTest {

    @RegisterExtension
    private static final TestRuntimeV2 runtime = new TestRuntimeV2();

    @Test
    public void testLazyValueAsTaskInVars() throws Exception {
        runtime.deploy("lazyValues/taskInOutVariables");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        assertLog(log, ".*should print real value: myValue.*");

        assertLog(log, ".*lazy value resole -> myValue @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> myValue @ TaskCall\\{name='lazyValuesTask'\\}.*");
        assertLogExactMatch(log, 2, ".*lazy value resole -> myValue.*");

        assertLog(log, ".*lazy value resole -> myOutValue @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> myOutValue.*");
        assertLog(log, ".*should print `myOutValue`: myOutValue.*");
    }

    @Test
    public void testLazyValueInSetStep() throws Exception {
        runtime.deploy("lazyValues/setStep");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        assertLogExactMatch(log, 2, ".*should print `myValue`: myValue.*");

        assertLog(log, ".*lazy value resole -> true \\@ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*0 should print `true`: true.*");

        assertLog(log, ".*lazy value resole -> 123 \\@ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*0 should print `123`: 123.*");

        assertLog(log, ".*should print `myValue_boom`: myValue_boom.*");
        assertLog(log, ".*1 should print `true`: true.*");
        assertLog(log, ".*2 should print `true`: true.*");
        assertLog(log, ".*3 should print `true`: true.*");
        assertLog(log, ".*4 should print `true`: true.*");
        assertLog(log, ".*5 should print `true`: true.*");

        assertLog(log, ".*lazy value resole -> myValue \\@ SetVariablesStep\\{vars=\\{nonLazyValue2=\\$\\{lazyValue == 'myValue' \\? true : false\\}\\}\\}.*");
        assertLog(log, ".*lazy value resole -> myValue \\@ SetVariablesStep\\{vars=\\{nonLazyValue3=\\$\\{lazyValue.trim\\(\\).length\\(\\) == 7 \\? true : false\\}\\}\\}.*");
        assertLogExactMatch(log, 6, ".*lazy value resole -> myValue.*");
    }

    @Test
    public void testLazyValueInIfStep() throws Exception {
        runtime.deploy("lazyValues/ifStep");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        assertLog(log, ".*myValue @ IfStep\\{expression='\\$\\{lazyValue != 'myValue'\\}'\\}.*");
        assertLog(log, ".*myValue @ IfStep\\{expression='\\$\\{lazyValue == 'myValue'\\}'\\}.*");
        assertLog(log, ".*EQ is ok.*");

        assertLog(log, ".*myValue @ IfStep\\{expression='\\$\\{lazyValue.trim\\(\\) == 'myValue'\\}'\\}.*");
        assertLog(log, ".*methods is ok.*");
    }

    @Test
    public void testLazyValueInCallStep() throws Exception {
        runtime.deploy("lazyValues/callStep");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        assertLog(log, ".*lazy value resole -> myValue @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> myValue.*");
        assertLog(log, ".*should print `myValue`: myValue.*");

        assertLog(log, ".*lazy value resole -> outValue @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> outValue.*");
        assertLog(log, ".*should print `outValue`: outValue.*");
    }

    @Test
    public void testLazyValueInScriptStep() throws Exception {
        runtime.deploy("lazyValues/scriptStep");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        // `in` variables automatically evaluated
        assertLog(log, ".*lazy value resole -> myValue @ ScriptCall\\{languageOrRef='js'\\}.*");
        assertLog(log, ".*lazy value resole -> myValue.*");
        assertLog(log, ".*should print `myValue`: myValue.*");

        //
        assertLog(log, ".*lazy value resole -> lazyValueFromScript @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> lazyValueFromScript.*");
        assertLog(log, ".*should print `lazyValueFromScript`: lazyValueFromScript.*");
    }

    @Test
    public void testLazyValueInExprStep() throws Exception {
        runtime.deploy("lazyValues/exprStep");

        runtime.save(ProcessConfiguration.builder().build());
        byte[] log = runtime.run();

        assertLog(log, ".*lazy value resole -> 100 @ Expression\\{expr='\\$\\{lazyValue \\+ 123\\}'\\}.*");
        assertLog(log, ".*lazy value resole -> 100 @ Expression\\{expr='\\$\\{lazyValue.trim\\(\\).length\\(\\)\\}'\\}.*");
        assertLog(log, ".*lazy value resole -> 100 @ Expression\\{expr='\\$\\{lazyValuesTask.assertNoLazyValuesInArgument\\( \\{'myKey': lazyValue\\} \\)\\}'\\}.*");
        assertLogExactMatch(log, 3, ".*lazy value resole -> 100.*");

        assertLog(log, ".*1 should print `true`: true.*");
        assertLog(log, ".*2 should print `true`: true.*");
        assertLog(log, ".*3 should print `true`: true.*");
        assertLog(log, ".*4 should print `3`: 3.*");

        assertLog(log, ".*5 should print `valueFromExpression`: valueFromExpression.*");
        assertLog(log, ".*lazy value resole -> valueFromExpression @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> valueFromExpression.*");

        assertLog(log, ".*6 should print `myValue`: myValue.*");
        assertLog(log, ".*lazy value resole -> myValue @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> myValue.*");

        assertLog(log, ".*lazy value resole -> lazyValueFromSetStep @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> lazyValueFromSetStep.*");

        assertLog(log, ".*lazy value resole -> lazyValueFromExprStep @ TaskCall\\{name='log'\\}.*");
        assertLog(log, ".*lazy value resole -> lazyValueFromExprStep.*");
    }
}
