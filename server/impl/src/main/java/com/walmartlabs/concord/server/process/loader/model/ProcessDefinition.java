package com.walmartlabs.concord.server.process.loader.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.imports.Imports;

import java.util.List;
import java.util.Map;

/**
 * Common interface for v1 and v2 process definitions.
 */
public interface ProcessDefinition {

    String runtime();

    Configuration configuration();

    Map<String, FlowDefinition> flows();

    Map<String, Profile> profiles();

    List<Trigger> triggers();

    Imports imports();
}
