package com.walmartlabs.concord.client;

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


import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface UpdateSecretRequestV2 extends UpdateSecretRequest {

    @Nullable
    List<String> newProjectNames();

    @Nullable
    List<UUID> newProjectIds();


    static ImmutableUpdateSecretRequestV2.Builder builder() {
        return ImmutableUpdateSecretRequestV2.builder();
    }
}
