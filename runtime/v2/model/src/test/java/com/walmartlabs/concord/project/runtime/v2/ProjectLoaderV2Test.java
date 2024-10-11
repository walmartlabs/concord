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

import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.Checkpoint;
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ProjectLoaderV2Test {

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiProjectFiles() throws Exception {
        ProjectLoaderV2 loader = new ProjectLoaderV2(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("multiProjectFile").toURI();
        ProjectLoaderV2.Result result = loader.load(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
        assertNotNull(result);
        assertNotNull(result.getProjectDefinition());

        ProcessDefinition pd = result.getProjectDefinition();

        // configuration:
        ProcessDefinitionConfiguration cfg = pd.configuration();
        assertNotNull(cfg);

        // configuration.debug: should be collected from ROOT concord.yml
        assertTrue(cfg.debug());

        // configuration.activeProfiles: should be collected from ROOT concord.yml
        assertEquals(Collections.singletonList("concord.yml"), cfg.activeProfiles());

        // configuration.entryPoint: should be collected from ROOT concord.yml
        assertEquals("root", cfg.entryPoint());

        // configuration.dependencies: should be collected from ALL *.concord.yml
        assertEquals(Arrays.asList("2.concord.yml", "concord.yml"), cfg.dependencies());

        // configuration.arguments: should be collected from ALL *.concord.yml and mereged
        assertEquals("ttt", cfg.arguments().get("abc"));
        assertEquals("234", ((Map<String, Object>) cfg.arguments().get("nested")).get("value"));

        // configuration.meta: should be collected from ROOT concord.yml
        assertEquals(Collections.singletonMap("k", "concord.yml"), cfg.meta());

        // configuration.events: should be collected from ROOT concord.yml
        assertEquals(EventConfiguration.builder()
                .recordTaskInVars(true)
                .recordTaskOutVars(true)
                .truncateInVars(true)
                .truncateOutVars(true)
                .truncateMaxStringLength(1)
                .truncateMaxArrayLength(2)
                .truncateMaxDepth(3)
                .inVarsBlacklist(Arrays.asList("apiKey", "apiRootToken"))
                .recordTaskMeta(true)
                .truncateMeta(true)
                .build(), cfg.events());

        // configuration.requirements: should be collected from ROOT concord.yml
        assertEquals(Collections.singletonMap("req", "concord.yml"), cfg.requirements());

        // configuration.processTimeout: should be collected from ROOT concord.yml
        assertEquals("PT1H", cfg.processTimeout().toString());

        // configuration.suspendTimeout: should be collected from ROOT concord.yml
        assertEquals("PT26H", cfg.suspendTimeout().toString());

        // configuration.out: should be collected from ROOT concord.yml
        assertEquals(Collections.singletonList("from-root"), cfg.out());

        // configuration.template: should be collected from ROOT concord.yml
        assertNotNull(cfg.template());
        assertEquals("mytemplate", cfg.template());

        // flows: should be collected from ALL *.concord.yml
        // if flow has same name then most recent used
        assertEquals(new HashSet<>(Arrays.asList("default", "flowN3")), pd.flows().keySet());
        assertEquals(1, pd.flows().get("default").size());
        assertEquals(pd.flows().size(), pd.flowsDefinition().size());
        assertInstanceOf(Checkpoint.class, pd.flows().get("default").get(0));
        assertEquals("root", ((Checkpoint) pd.flows().get("default").get(0)).getName());

        // publicFlows: should be collected from ROOT concord.yml
        assertEquals(Collections.singleton("root"), pd.publicFlows());

        // profiles: should be collected from ALL *.concord.yml
        // if profile has same name then most recent used
        assertEquals(Collections.emptyMap(), pd.profiles());

        // triggers: should be collected from ALL *.concord.yml
        assertEquals(3, pd.triggers().size());
        assertEquals(Arrays.asList("1.concord.yml", "2.concord.yml", "concord.yml"), pd.triggers().stream().map(t -> t.configuration().get("entryPoint")).collect(Collectors.toList()));

        // imports: should be collected from ALL *.concord.yml
        assertEquals(2, pd.imports().items().size());
        assertEquals(Arrays.asList("2.concord.yml", "concord.yml"), pd.imports().items().stream().map(i -> ((Import.GitDefinition) i).url()).collect(Collectors.toList()));

        // forms: should be collected from ALL *.concord.yml
        // if form has same name then most recent used
        assertEquals(1, pd.forms().size());
        assertNotNull(pd.forms().get("myForm"));
        assertEquals(1, pd.forms().get("myForm").fields().size());
        assertEquals("myName3", pd.forms().get("myForm").fields().get(0).name());

        // resources: should be collected from ALL *.concord.yml
        assertEquals(Arrays.asList("glob:concord/{**/,}{*.,}concord.{yml,yaml}", "glob:tmp/1.yml"), pd.resources().concord());
    }
}
