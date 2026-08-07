package com.walmartlabs.concord.runtime.v25.runner.expression;

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

import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v25.runner.scope.Scope;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionServiceTest {

    @Test
    void preservesPureTypesAndRecursivelyEvaluatesContainers() {
        var expressions = new ExpressionService();
        var scope = Scope.root(Map.of("number", 2, "name", "Concord"), Set.of("default"),
                "default", false, true, null);
        var source = Map.of(
                "pure", "${number + 1}",
                "text", "hello ${name}",
                "items", List.of("${number}", Map.of("value", "${name}")));

        var result = expressions.evaluate(source, scope);

        assertEquals(3L, ((Map<?, ?>) result).get("pure"));
        assertEquals("hello Concord", ((Map<?, ?>) result).get("text"));
        assertEquals(List.of(2, Map.of("value", "Concord")), ((Map<?, ?>) result).get("items"));
    }

    @Test
    void supportsStreamsStaticFieldsAndNestedAssignmentsWithoutMutatingFrozenValues() {
        var expressions = new ExpressionService();
        var source = Map.of("x", 1, "items", List.of(Map.of("x", 2), Map.of("x", 3)));
        var scope = Scope.root(Map.of("aVar", source, "list", source.get("items")), Set.of("default"),
                "default", false, false, null);

        assertEquals(List.of(2, 3), expressions.evaluate("${list.stream().map(o -> o.x).toList()}", scope));
        assertEquals(Integer.MAX_VALUE, expressions.evaluate("${Integer.MAX_VALUE}", scope));
        assertEquals(2L, expressions.evaluate("${aVar.x = aVar.x + 1}", scope));
        assertEquals(2L, ((Map<?, ?>) scope.get("aVar")).get("x"));
        assertEquals(1, source.get("x"));
    }


    @Test
    void assignsLiteralDottedKeysAndUsesTheActualAliasedAccessPath() {
        var expressions = new ExpressionService();
        var shared = new java.util.LinkedHashMap<String, Object>();
        var scope = Scope.root(Map.of("first", shared, "second", shared, "cfg", Map.of()),
                Set.of("default"), "default", false, false, null);

        expressions.evaluate("${second['a.b'] = 2}", scope);
        expressions.evaluate("${cfg['a.b'] = 3}", scope);

        assertFalse(((Map<?, ?>) scope.get("first")).containsKey("a.b"));
        assertEquals(2L, ((Map<?, ?>) scope.get("second")).get("a.b"));
        assertEquals(3L, ((Map<?, ?>) scope.get("cfg")).get("a.b"));
    }

    @Test
    void assignsThroughCyclicMapsWithoutRecursingDuringPathResolution() {
        var expressions = new ExpressionService();
        var cyclic = new java.util.LinkedHashMap<String, Object>();
        cyclic.put("self", cyclic);
        var scope = Scope.root(Map.of("node", cyclic), Set.of("default"), "default", false, false, null);

        expressions.evaluate("${node.self.value = 2}", scope);

        var node = (Map<?, ?>) scope.get("node");
        assertEquals(2L, ((Map<?, ?>) node.get("self")).get("value"));
    }
    @Test
    void evaluatesEvalAsMapValuesAndDeepMergesScopeMappings() {
        var expressions = new ExpressionService();
        var scope = Scope.root(Map.of("a", Map.of("out1", "evaluated", "existing", "keep"),
                        "x", Map.of("a", Map.of("out1", "${a.out1}", "newValue", "${a.out1}"))),
                Set.of("default"), "default", false, false, null);

        var result = expressions.evaluate("${evalAsMap(x)}", scope);

        assertEquals(Map.of("out1", "evaluated", "existing", "keep", "newValue", "evaluated"),
                ((Map<?, ?>) result).get("a"));
    }

    @Test
    void implementsNullAwareHelpersAndCachesParsedExpressions() {
        var expressions = new ExpressionService();
        var scope = Scope.root(Map.of(), Set.of("default"), "default", false, true, null);
        scope.set("nullable", null);

        assertEquals(true, expressions.evaluate("${hasVariable('nullable')}", scope));
        assertEquals(false, expressions.evaluate("${hasNonNullVariable('nullable')}", scope));
        assertEquals("fallback", expressions.evaluate("${orDefault('nullable', 'fallback')}", scope));
        assertEquals("default", expressions.evaluate("${currentFlowName()}", scope));
        assertEquals(true, expressions.evaluate("${hasFlow('default')}", scope));
        assertEquals(true, expressions.evaluate("${isDebug()}", scope));
        assertNull(expressions.evaluate("${nullable}", scope));
        assertFalse((Boolean) expressions.evaluate("${hasVariable('missing')}", scope));

        var before = expressions.compiledExpressionCount();
        expressions.evaluate("${nullable}", scope);
        assertEquals(before, expressions.compiledExpressionCount());
        assertTrue(before > 0);
    }

    @Test
    void restoresOuterFunctionScopeAfterNestedEvaluation() {
        var methods = new NestedTaskMethods();
        var expressions = new ExpressionService(methods);
        methods.expressions = expressions;
        var scope = Scope.root(Map.of("outer", true), Set.of("default"), "default", false, false, null);

        assertEquals(true, expressions.evaluate("${task.invoke() and hasVariable('outer')}", scope));
    }

    @Test
    void resolvesSdkFunctionsUsingV2AnnotationNames() throws Exception {
        var functions = List.<Method>of(
                SdkFunctions.class.getMethod("defaultName", String.class),
                SdkFunctions.class.getMethod("prefixed", String.class));
        var expressions = new ExpressionService(ExpressionService.TaskMethods.NONE, functions);
        var scope = Scope.root(Map.of(), Set.of("default"), "default", false, false, null);

        assertEquals("default:Ada", expressions.evaluate("${defaultName('Ada')}", scope));
        assertEquals("prefix:Ada", expressions.evaluate("${sdk:greet('Ada')}", scope));
    }

    @Test
    void rejectsInvalidAndDuplicateSdkFunctions() throws Exception {
        var nonStatic = NonStaticFunction.class.getMethod("invalid");
        var nonStaticFailure = assertThrows(IllegalArgumentException.class,
                () -> new ExpressionService(ExpressionService.TaskMethods.NONE, List.of(nonStatic)));
        assertEquals("@ELFunction method must be static: "
                + NonStaticFunction.class.getName() + ".invalid", nonStaticFailure.getMessage());

        var nonPublic = NonPublicFunction.class.getDeclaredMethod("invalid");
        var nonPublicFailure = assertThrows(IllegalArgumentException.class,
                () -> new ExpressionService(ExpressionService.TaskMethods.NONE, List.of(nonPublic)));
        assertEquals("@ELFunction method must be public: "
                + NonPublicFunction.class.getName() + ".invalid", nonPublicFailure.getMessage());

        var duplicateFailure = assertThrows(IllegalArgumentException.class,
                () -> new ExpressionService(ExpressionService.TaskMethods.NONE, List.of(
                        DuplicateB.class.getMethod("second"), DuplicateA.class.getMethod("first"))));
        assertEquals("Duplicate @ELFunction name 'duplicate': " + DuplicateA.class.getName()
                + ".first and " + DuplicateB.class.getName() + ".second", duplicateFailure.getMessage());
    }

    private static final class NestedTaskMethods implements ExpressionService.TaskMethods {

        private ExpressionService expressions;

        @Override
        public boolean hasTask(String name) {
            return "task".equals(name);
        }

        @Override
        public Object invoke(String taskName, String methodName, Object[] arguments, Scope scope) {
            var child = scope.child(scope.flowName());
            child.set("inner", true);
            return expressions.evaluate("${hasVariable('inner')}", child);
        }
    }

    public static final class SdkFunctions {

        @ELFunction
        public static String defaultName(String value) {
            return "default:" + value;
        }

        @ELFunction("sdk:greet")
        public static String prefixed(String value) {
            return "prefix:" + value;
        }
    }

    public static final class NonStaticFunction {

        @ELFunction
        public String invalid() {
            return "invalid";
        }
    }

    public static final class NonPublicFunction {

        @ELFunction
        private static String invalid() {
            return "invalid";
        }
    }

    public static final class DuplicateA {

        @ELFunction("duplicate")
        public static String first() {
            return "first";
        }
    }

    public static final class DuplicateB {

        @ELFunction("duplicate")
        public static String second() {
            return "second";
        }
    }
}
