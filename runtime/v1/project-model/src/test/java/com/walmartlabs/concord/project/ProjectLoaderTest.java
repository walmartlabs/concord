package com.walmartlabs.concord.project;

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
import com.walmartlabs.concord.project.model.ProjectDefinition;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.YamlParserException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class ProjectLoaderTest {

    @Test
    public void testSimple() throws Exception {
        ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("simple").toURI();
        ProjectDefinition pd = loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER)
                .getProjectDefinition();

        assertNotNull(pd);

        assertNotNull(pd.getFlows().get("main"));
        assertNotNull(pd.getFlows().get("other"));

        assertNotNull(pd.getForms().get("myForm"));
    }

    @Test
    public void testEmptyField() {
        assertThrows(YamlConverterException.class, () -> {
            ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

            URI uri = ClassLoader.getSystemResource("emptyField").toURI();
            loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
        });
    }

    @Test
    public void testDuplicateConfigurationSection() throws Exception {
        ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("duplicateConfiguration").toURI();
        try {
            loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
            fail("exception expected");
        } catch (YamlParserException e) {
            assertTrue(e.getMessage().contains("Duplicate field 'configuration'"));
        }
    }

    @Test
    public void testDuplicateConfigurationVariable() throws Exception {
        ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("duplicateConfigurationVariable").toURI();
        try {
            loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER);
            fail("exception expected");
        } catch (YamlParserException e) {
            assertTrue(e.getMessage().contains("Duplicate field 'x'"));
        }
    }

    @Test
    public void testComplex() throws Exception {
        ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("complex").toURI();
        ProjectDefinition pd = loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER).getProjectDefinition();
        assertNotNull(pd);

        //--- triggers
        assertNotNull(pd.getTriggers());
        assertEquals(4, pd.getTriggers().size());
        assertEquals("manual", pd.getTriggers().get(3).getName());
        assertEquals("integration", pd.getTriggers().get(3).getEntryPoint());
        assertEquals("Run Integration Tests", pd.getTriggers().get(3).getCfg().get("name"));

        //--- imports
        assertNotNull(pd.getImports());
        assertEquals(3, pd.getImports().items().size());
        assertEquals("git", pd.getImports().items().get(0).type());

        assertNotNull(pd.getResources());
        assertEquals(1, pd.getResources().getDefinitionPaths().size());
        assertEquals("myFlows", pd.getResources().getDefinitionPaths().get(0));
        assertEquals(1, pd.getResources().getDisabledDirs().size());
        assertEquals("processes", pd.getResources().getDisabledDirs().get(0));
        assertEquals(1, pd.getResources().getProfilesPaths().size());
        assertEquals("myProfiles", pd.getResources().getProfilesPaths().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiProjectFiles() throws Exception {
        ProjectLoader loader = new ProjectLoader(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("multiProjectFile").toURI();
        ProjectDefinition pd = loader.loadProject(Paths.get(uri), new NoopImportsNormalizer(), ImportsListener.NOP_LISTENER).getProjectDefinition();
        assertNotNull(pd);

        assertNotNull(pd.getFlows().get("default"));
        assertNotNull(pd.getForms().get("myForm"));
        assertFalse(pd.getTriggers().isEmpty());

        Map<String, Object> cfg = pd.getConfiguration();
        assertNotNull(cfg);
        assertEquals("ttt", ((Map<String, Object>) cfg.get("arguments")).get("abc"));
        assertEquals("234", ((Map<String, Object>) ((Map<String, Object>) cfg.get("arguments")).get("nested")).get("value"));
    }
}
