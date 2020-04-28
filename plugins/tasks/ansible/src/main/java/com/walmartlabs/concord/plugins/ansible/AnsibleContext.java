package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.common.AllowNulls;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.sdk.MapUtils.getBoolean;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface AnsibleContext {

    UUID instanceId();

    Path workDir();

    Path tmpDir();

    default boolean debug() {
        return getBoolean(args(), TaskParams.DEBUG_KEY.getKey(), false);
    }

    @AllowNulls
    Map<String, Object> defaults();

    @AllowNulls
    Map<String, Object> args();

    @Nullable
    String sessionToken();

    @Nullable
    UUID eventCorrelationId();

    @Nullable
    String orgName();

    @Nullable
    Integer retryCount();

    static ImmutableAnsibleContext.Builder builder() {
        return ImmutableAnsibleContext.builder();
    }
}
