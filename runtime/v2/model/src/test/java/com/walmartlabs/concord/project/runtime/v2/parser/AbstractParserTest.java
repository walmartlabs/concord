package com.walmartlabs.concord.project.runtime.v2.parser;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.ProjectLoaderV2;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;

import java.net.URI;
import java.nio.file.Paths;

public abstract class AbstractParserTest {

    protected static ProcessDefinition load(String resource) throws Exception {
        URI uri = ClassLoader.getSystemResource(resource).toURI();

        ProjectLoaderV2 loader = new ProjectLoaderV2();
        return loader.loadFromFile(Paths.get(uri)).getProjectDefinition();
    }
}
