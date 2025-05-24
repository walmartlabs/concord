package com.walmartlabs.concord.process.loader;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

public final class StandardRuntimeTypes {

    public static final String CONCORD_V1_RUNTIME_TYPE = "concord-v1";
    public static final String CONCORD_V2_RUNTIME_TYPE = "concord-v2";

    /**
     * Files that the runtime considers "root" project files.
     */
    public static final String[] PROJECT_ROOT_FILE_NAMES = {
            ".concord.yml",
            "concord.yml",
            ".concord.yaml",
            "concord.yaml"
    };


    private StandardRuntimeTypes() {
    }
}
