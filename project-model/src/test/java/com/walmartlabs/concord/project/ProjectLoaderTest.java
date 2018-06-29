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

import com.walmartlabs.concord.project.model.ProjectDefinition;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ProjectLoaderTest {

    @Test
    public void testSimple() throws Exception {
        ProjectLoader loader = new ProjectLoader();

        URI uri = ClassLoader.getSystemResource("simple").toURI();
        ProjectDefinition pd = loader.load(Paths.get(uri));

        assertNotNull(pd);

        assertNotNull(pd.getFlows().get("main"));
        assertNotNull(pd.getFlows().get("other"));

        assertNotNull(pd.getForms().get("myForm"));
    }

    @Test
    public void testEmptyField() throws Exception {
        ProjectLoader loader = new ProjectLoader();

        URI uri = ClassLoader.getSystemResource("emptyField").toURI();
        ProjectDefinition pd = loader.load(Paths.get(uri));

        assertNotNull(pd);

        assertNotNull(pd.getFlows().get("main"));
        assertNotNull(pd.getFlows().get("other"));

        assertNotNull(pd.getForms().get("myForm"));
    }

    @Test
    public void testComplex() throws Exception {
        ProjectLoader loader = new ProjectLoader();

        URI uri = ClassLoader.getSystemResource("complex").toURI();
        ProjectDefinition pd = loader.load(Paths.get(uri));
        assertNotNull(pd);

        assertNotNull(pd.getTriggers());
        assertEquals(2, pd.getTriggers().size());
    }
}
