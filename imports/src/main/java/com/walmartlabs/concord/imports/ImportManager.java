package com.walmartlabs.concord.imports;

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

import com.walmartlabs.concord.repository.Snapshot;

import java.nio.file.Path;
import java.util.List;

public interface ImportManager {

    /**
     * Process the specified imports and save the result into {@code dest}.
     * Assumes all import definitions were normalized (i.e. contain valid URLs, secret/org names, etc).
     */
    List<Snapshot> process(Imports imports, Path dest, ImportsListener listener) throws Exception;
}
