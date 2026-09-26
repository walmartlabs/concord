package com.walmartlabs.concord.project.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.runtime.model.EffectiveConfiguration;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.wrapper.ProcessDefinitionV2;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

public class ProfileOverlayTest {

    @Test
    public void testNoActiveProfiles() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(Collections.emptyList());

        assertTopLevelValues(cfg);
        assertEquals(List.of("mvn://base:base:1.0"), cfg.get("dependencies"));
    }

    /**
     * A profile must only override the values its author actually specified. Everything
     * else must be inherited from the top-level {@code configuration} block.
     */
    @Test
    public void testProfileDoesNotResetUnspecifiedValues() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(List.of("onlyDeps"));

        assertTopLevelValues(cfg);
        assertEquals(List.of("mvn://profile:profile:1.0"), cfg.get("dependencies"));
    }

    /**
     * A value explicitly specified in a profile must win, even when it is equal to the
     * attribute's default value.
     */
    @Test
    public void testProfileOverridesExplicitValues() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(List.of("explicitDebug"));

        assertEquals(false, cfg.get("debug"));
        assertEquals("myEntry", cfg.get("entryPoint"));
    }

    /**
     * A partially specified nested block must only override the nested keys it actually
     * specifies, the rest must be inherited from the top-level block.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testProfileDoesNotResetUnspecifiedNestedValues() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(List.of("partialEvents"));

        Map<String, Object> events = (Map<String, Object>) cfg.get("events");
        assertEquals(false, events.get("recordTaskOutVars"));
        assertEquals(7, events.get("batchSize"));
        assertEquals(true, events.get("recordTaskInVars"));

        assertEquals(true, cfg.get("debug"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testProfileDoesNotResetUnspecifiedNestedRecordValues() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(List.of("partialValidation"));

        Map<String, Object> taskCalls = (Map<String, Object>) ((Map<String, Object>) cfg.get("validation")).get("taskCalls");
        assertEquals("DISABLED", taskCalls.get("out"));
        assertEquals("FAIL", taskCalls.get("in"));
    }

    /**
     * A specified nested list is replaced as a whole -- neither merged with the top-level
     * one nor reset to the attribute's default -- and its siblings are left alone.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testProfileReplacesNestedList() throws Exception {
        Map<String, Object> cfg = effectiveConfiguration(List.of("nestedList"));

        Map<String, Object> events = (Map<String, Object>) cfg.get("events");
        assertEquals(List.of("profileSecret"), events.get("inVarsBlacklist"));
        assertEquals(true, events.get("recordTaskInVars"));
        assertEquals(true, events.get("recordTaskOutVars"));
    }

    @SuppressWarnings("unchecked")
    private static void assertTopLevelValues(Map<String, Object> cfg) {
        assertEquals(true, cfg.get("debug"));
        assertEquals("myEntry", cfg.get("entryPoint"));
        assertEquals(3, cfg.get("parallelLoopParallelism"));

        Map<String, Object> events = (Map<String, Object>) cfg.get("events");
        assertEquals(true, events.get("recordTaskInVars"));
        assertEquals(true, events.get("recordTaskOutVars"));
        assertEquals(List.of("baseSecret", "baseToken"), events.get("inVarsBlacklist"));

        Map<String, Object> taskCalls = (Map<String, Object>) ((Map<String, Object>) cfg.get("validation")).get("taskCalls");
        assertEquals("FAIL", taskCalls.get("in"));
        assertEquals("WARN", taskCalls.get("out"));
    }

    private static Map<String, Object> effectiveConfiguration(Collection<String> activeProfiles) throws Exception {
        ProjectLoaderV2 loader = new ProjectLoaderV2(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("profileOverlay").toURI();
        ProjectLoaderV2.Result result = loader.load(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);

        return EffectiveConfiguration.getEffectiveConfiguration(
                new ProcessDefinitionV2(result.getProjectDefinition()), activeProfiles);
    }
}
