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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.imports.Import;
import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.Checkpoint;
import com.walmartlabs.concord.runtime.v2.model.EventConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinitionConfiguration;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
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
        assertEquals(ImmutableList.of("concord.yml"), cfg.activeProfiles());

        // configuration.entryPoint: should be collected from ROOT concord.yml
        assertEquals("root", cfg.entryPoint());

        // configuration.dependencies: should be collected from ALL *.concord.yml
        assertEquals(ImmutableList.of("2.concord.yml", "concord.yml"), cfg.dependencies());

        // configuration.arguments: should be collected from ALL *.concord.yml and mereged
        assertEquals("ttt", cfg.arguments().get("abc"));
        assertEquals("234", ((Map<String, Object>) cfg.arguments().get("nested")).get("value"));

        // configuration.meta: should be collected from ROOT concord.yml
        assertEquals(ImmutableMap.of("k", "concord.yml"), cfg.meta());

        // configuration.events: should be collected from ROOT concord.yml
        assertEquals(EventConfiguration.builder()
                .recordTaskInVars(true)
                .recordTaskOutVars(true)
                .truncateInVars(true)
                .truncateOutVars(true)
                .truncateMaxStringLength(1)
                .truncateMaxArrayLength(2)
                .truncateMaxDepth(3)
                .inVarsBlacklist(ImmutableList.of("apiKey", "apiRootToken"))
                .recordTaskMeta(true)
                .truncateMeta(true)
                .build(), cfg.events());

        // configuration.requirements: should be collected from ROOT concord.yml
        assertEquals(ImmutableMap.of("req", "concord.yml"), cfg.requirements());

        // configuration.requirements: should be collected from ROOT concord.yml
        assertEquals("PT1H", cfg.processTimeout().toString());

        // configuration.out: should be collected from ROOT concord.yml
        assertEquals(ImmutableList.of("from-root"), cfg.out());

        // configuration.template: should be collected from ROOT concord.yml
        assertNotNull(cfg.template());
        assertEquals("mytemplate", cfg.template());

        // flows: should be collected from ALL *.concord.yml
        // if flow has same name then most recent used
        assertEquals(ImmutableSet.of("default", "flowN3"), pd.flows().keySet());
        assertEquals(1, pd.flows().get("default").size());
        assertTrue(pd.flows().get("default").get(0) instanceof Checkpoint);
        assertEquals("root", ((Checkpoint) pd.flows().get("default").get(0)).getName());

        // publicFlows: should be collected from ROOT concord.yml
        assertEquals(ImmutableSet.of("root"), pd.publicFlows());

        // profiles: should be collected from ALL *.concord.yml
        // if profile has same name then most recent used
        assertEquals(ImmutableMap.of(), pd.profiles());

        // triggers: should be collected from ALL *.concord.yml
        assertEquals(3, pd.triggers().size());
        assertEquals(ImmutableList.of("1.concord.yml", "2.concord.yml", "concord.yml"), pd.triggers().stream().map(t -> t.configuration().get("entryPoint")).collect(Collectors.toList()));

        // imports: should be collected from ALL *.concord.yml
        assertEquals(2, pd.imports().items().size());
        assertEquals(ImmutableList.of("2.concord.yml", "concord.yml"), pd.imports().items().stream().map(i -> ((Import.GitDefinition)i).url()).collect(Collectors.toList()));

        // forms: should be collected from ALL *.concord.yml
        // if form has same name then most recent used
        assertEquals(1, pd.forms().size());
        assertNotNull(pd.forms().get("myForm"));
        assertEquals(1, pd.forms().get("myForm").fields().size());
        assertNotNull("name3", pd.forms().get("myForm").fields().get(0).name());

        // resources: should be collected from ALL *.concord.yml
        assertEquals(ImmutableList.of("glob:concord/{**/,}{*.,}concord.{yml,yaml}", "glob:tmp/1.yml"), pd.resources().concord());
    }
}
