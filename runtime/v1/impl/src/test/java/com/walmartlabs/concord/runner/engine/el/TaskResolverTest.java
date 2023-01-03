package com.walmartlabs.concord.runner.engine.el;

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

import com.walmartlabs.concord.sdk.InjectVariable;
import io.takari.bpm.task.ServiceTaskRegistryImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.el.ELContext;

import static org.mockito.Mockito.spy;
import static org.junit.jupiter.api.Assertions.*;

public class TaskResolverTest extends AbstractElResolverTest {

    private ServiceTaskRegistryImpl registry;
    private ELContext elContext;
    private TaskResolver resolver;

    @BeforeEach
    public void setUp() {
        registry = new ServiceTaskRegistryImpl();
        resolver = new TaskResolver(registry);
        elContext = createContext();
    }

    @Test
    public void test() {
        TestTask task = spy(new TestTask());
        registry.register("test", task);

        mockVariables("var1", "var1-value");

        resolver.getValue(elContext, null, "test");

        assertEquals("var1-value", task.value);
    }

    @SuppressWarnings("unused")
    private static class TestTask {

        @InjectVariable("var1")
        private String value;

        public void setValue(String value) {
            this.value = value;
        }
    }
}
