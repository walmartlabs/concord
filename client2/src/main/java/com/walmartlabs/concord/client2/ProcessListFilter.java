package com.walmartlabs.concord.client2;

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

import com.walmartlabs.concord.server.OffsetDateTimeParam;
import com.walmartlabs.concord.server.process.ProcessDataInclude;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value.Immutable
public interface ListProcessFilter {

    UUID orgId();

    String orgName();

    UUID projectId();

    String projectName();

    UUID repoId();

    String repoName();

    OffsetDateTimeParam afterCreatedAt();

    OffsetDateTimeParam beforeCreatedAt();

    Set<String> tags();

    ProcessStatus status();

    String initiator();

    UUID parentInstanceId();

    Set<ProcessDataInclude> include();

    Integer limit();

    Integer offset();

    Map<String, String> meta();
}
