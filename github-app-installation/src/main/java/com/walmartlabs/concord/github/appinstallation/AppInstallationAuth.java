package com.walmartlabs.concord.github.appinstallation;

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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(jdkOnly = true)
@JsonDeserialize(as = ImmutableAppInstallationAuth.class)
public interface AppInstallationAuth extends ExternalTokenAuth {

    @Value.Default
    default String apiUrl() {
        return "https://api.github.com";
    }

    String clientId();

    String privateKey();

    @Value.Check
    default void checkUrlPattern() {
        // sanity check url pattern before this object gets too far out there
        if (!urlPattern().toString().contains("?<baseUrl>")) {
            throw new IllegalArgumentException("The url pattern must contain the ?<baseUrl> named group");
        }
    }

    static ImmutableAppInstallationAuth.Builder builder() {
        return ImmutableAppInstallationAuth.builder();
    }

}
