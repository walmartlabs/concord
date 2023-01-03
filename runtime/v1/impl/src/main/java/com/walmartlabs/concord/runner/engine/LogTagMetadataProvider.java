package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.sdk.Context;

import java.util.Map;

/**
 * Provides additional metadata for the process log "tags".
 */
public interface LogTagMetadataProvider {

    /**
     * Collects the task's tag metadata using the provided context.
     * Don't expose any sensitive variables in this method as the returned data
     * will be saved into the task's "log tag" in the process' log as is, without
     * any filtering.
     */
    Map<String, Object> createLogTagMetadata(Context ctx);
}
