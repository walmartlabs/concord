package com.walmartlabs.concord.sdk;

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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ContextUtilsTest {

    @Test
    public void testGetInt() {
        Context ctx = new Ctx();
        ctx.setVariable("k", 123);
        ctx.setVariable("not-int", 123L);

        // ---
        Integer result = ContextUtils.getVariable(ctx, "k", null, Integer.class);
        assertNotNull(result);
        assertEquals((Integer) 123, result);

        // ---
        result = ContextUtils.getVariable(ctx, "not-found", null, Integer.class);
        assertNull(result);

        // ---
        result = ContextUtils.getVariable(ctx, "not-found", -1, Integer.class);
        assertNotNull(result);
        assertEquals((Integer) (-1), result);

        // ---
        int res = ContextUtils.getInt(ctx, "k", -1);
        assertEquals(123, res);

        // ---
        res = ContextUtils.getInt(ctx, "not-found", -1);
        assertEquals(-1, res);

        // ---
        try {
            ContextUtils.getVariable(ctx, "not-int", null, Integer.class);
            fail("exception expected");
        } catch (IllegalArgumentException e) {
            // do nothing
        }
    }

    @Test
    public void testGetNumber() {
        Context ctx = new Ctx();
        ctx.setVariable("k", 123L);
        ctx.setVariable("not-number", "boom");

        // ---
        Number result = ContextUtils.getVariable(ctx, "k", null, Number.class);
        assertNotNull(result);
        assertEquals(123L, result);

        // ---
        result = ContextUtils.getVariable(ctx, "not-found", null, Number.class);
        assertNull(result);

        // ---
        result = ContextUtils.getVariable(ctx, "not-found", -1, Number.class);
        assertNotNull(result);
        assertEquals(-1, result);


        // ---
        result = ContextUtils.getNumber(ctx, "k", -1);
        assertEquals(123L, result);

        // ---
        result = ContextUtils.getNumber(ctx, "not-found", -1);
        assertEquals(-1, result);

        // ---
        try {
            ContextUtils.getVariable(ctx, "not-number", null, Number.class);
            fail("exception expected");
        } catch (IllegalArgumentException e) {
            // do nothing
        }

        // ---
        result = ContextUtils.assertNumber(ctx, "k");
        assertNotNull(result);
        assertEquals(123L, result);

        // ---
        try {
            ContextUtils.assertNumber(ctx, "not-found");
            fail("exception expected");
        } catch (IllegalArgumentException e) {
            // do nothing
        }
    }

    private static class Ctx implements Context {

        private final Map<String, Object> vars = new HashMap<>();

        @Override
        public Object getVariable(String key) {
            return vars.get(key);
        }

        @Override
        public void setVariable(String key, Object value) {
            vars.put(key, value);
        }

        @Override
        public void setProtectedVariable(String key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getProtectedVariable(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeVariable(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> getVariableNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T eval(String expr, Class<T> type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object interpolate(Object v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object interpolate(Object v, Map<String, Object> variables) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, Object> toMap() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void suspend(String eventName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void suspend(String eventName, Object payload, boolean restoreFromSameStep) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProcessDefinitionId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getElementId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void form(String formName, Map<String, Object> formOptions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getCurrentFlowName() {
            throw new UnsupportedOperationException();
        }
    }
}
