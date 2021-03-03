package com.walmartlabs.concord.runtime.v2.runner.el;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.runtime.v2.runner.el.functions.HasVariableFunction;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HasVariableFunctionTest {

    @Test
    public void test() {
        withVariables(Collections.singletonMap("test", "123"), () -> {
            assertTrue(HasVariableFunction.hasVariable("test"));
            assertFalse(HasVariableFunction.hasVariable("test.nested.deep"));
            assertFalse(HasVariableFunction.hasVariable("boom"));
        });

        withVariables(Collections.singletonMap("a", ConfigurationUtils.toNested("b.c", "123")), () -> {
            assertTrue(HasVariableFunction.hasVariable("a"));
            assertTrue(HasVariableFunction.hasVariable("a.b"));
            assertTrue(HasVariableFunction.hasVariable("a.b.c"));
            assertFalse(HasVariableFunction.hasVariable("a.b.c.d"));
            assertFalse(HasVariableFunction.hasVariable(""));
            assertFalse(HasVariableFunction.hasVariable(null));
        });
    }

    public static void withVariables(Map<String, Object> variables, Runnable runnable) throws RuntimeException {
        ThreadLocalEvalContext.withEvalContext(new EvalContext() {
            @Nullable
            @Override
            public Context context() {
                return null;
            }

            @Override
            public Variables variables() {
                return new MapBackedVariables(variables);
            }
        }, () -> {
            runnable.run();
            return null;
        });
    }
}
