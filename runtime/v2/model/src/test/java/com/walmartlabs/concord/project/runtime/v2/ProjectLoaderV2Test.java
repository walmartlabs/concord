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
import com.walmartlabs.concord.runtime.v2.NoopImportsNormalizer;
import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class ProjectLoaderV2Test {

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiProjectFiles() throws Exception {
        ProjectLoaderV2 loader = new ProjectLoaderV2(mock(ImportManager.class));

        URI uri = ClassLoader.getSystemResource("multiProjectFile").toURI();
        ProjectLoaderV2.Result result = loader.load(Paths.get(uri), new NoopImportsNormalizer());
        assertNotNull(result);
        assertNotNull(result.getProjectDefinition());

        ProcessDefinition pd = result.getProjectDefinition();
        assertNotNull(pd.flows().get("default"));
        assertNotNull(pd.forms().get("myForm"));
        assertNotNull(pd.publicFlows().iterator().next());

        ProcessConfiguration cfg = pd.configuration();
        assertNotNull(cfg);
        assertEquals("ttt", cfg.arguments().get("abc"));
        assertEquals("234", ((Map<String, Object>) cfg.arguments().get("nested")).get("value"));
        assertNotNull(cfg.template());
        assertEquals("mytemplate", cfg.template());
    }
}
