package com.walmartlabs.concord.runtime.v2.runner.tasks;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TaskCallInterceptorTest {

    @Test
    public void methodAnnotationsTest() {
        Base base = new Base();
        String method = "varargs";
        List<Object> params = Arrays.asList("one", "two");

        TaskCallInterceptor.Method m = TaskCallInterceptor.Method.of(base, method, params);

        assertEquals(0, m.annotations().size());
    }

    public static class Base {

        public void varargs(Object ... args) {

        }
    }
}
