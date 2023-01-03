package com.walmartlabs.concord.agent.guice;

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

import com.walmartlabs.concord.imports.ImportManager;
import com.walmartlabs.concord.imports.Imports;
import com.walmartlabs.concord.imports.ImportsListener;
import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;
import java.util.List;

/**
 * A wrapper type to avoid clashes with the Server's instance of a {@link ImportManager}.
 * TODO replace with a common Guice module
 */
public class AgentImportManager {

    private final ImportManager delegate;

    public AgentImportManager(ImportManager delegate) {
        this.delegate = delegate;
    }

    public List<Snapshot> process(Imports imports, Path dest) throws Exception {
        return delegate.process(imports, dest, ImportsListener.NOP_LISTENER);
    }
}
