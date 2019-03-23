package com.walmartlabs.concord.server.user;

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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;

public interface UserInfoProducer {

    UserType getUserType();

    UserInfo getInfo(String username);

    @Value.Immutable
    interface UserInfo {

        UserInfo EMPTY = UserInfo.builder().build();

        @Nullable
        String displayName();

        @Nullable
        String email();

        @Nullable
        Map<String, Object> attributes();

        static ImmutableUserInfo.Builder builder() {
            return ImmutableUserInfo.builder();
        }
    }
}
