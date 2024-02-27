package com.walmartlabs.concord.policyengine;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSerialize(as = ImmutableWorkspaceRule.class)
@JsonDeserialize(as = ImmutableWorkspaceRule.class)
public interface WorkspaceRule extends Serializable {

    long serialVersionUID = 1L;

    @Nullable
    String msg();

    @Nullable
    Long maxSizeInBytes();

    @Value.Default
    default Set<String> ignoredFiles() {
        return Collections.emptySet();
    }

    static WorkspaceRule of(String msg, Long maxSizeInBytes, Set<String> ignoredFiles) {
        return ImmutableWorkspaceRule.builder()
                .msg(msg)
                .maxSizeInBytes(maxSizeInBytes)
                .ignoredFiles(ignoredFiles != null ? ignoredFiles : Collections.emptySet())
                .build();
    }
}
