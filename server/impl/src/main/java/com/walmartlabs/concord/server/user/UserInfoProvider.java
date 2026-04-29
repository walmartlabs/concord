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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

public interface UserInfoProvider {

    UserType getUserType();

    /**
     * Returns data for the specified user.
     * @param id user's ID, optional
     * @param username user's name, mandatory
     * @param userDomain user's domain, optional
     * @return UserInfo user's info
     */
    UserInfo getInfo(UUID id, String username, String userDomain);

    UUID create(String username, String domain, String displayName, String email, Set<String> roles);

    @Value.Immutable
    @JsonInclude(NON_EMPTY)
    @JsonSerialize(as = ImmutableUserInfo.class)
    @JsonDeserialize(as = ImmutableUserInfo.class)
    interface UserInfo {

        static ImmutableUserInfo.Builder builder() {
            return ImmutableUserInfo.builder();
        }

        @Nullable
        UUID id();

        @Nullable
        String username();

        @Nullable
        String userDomain();

        @Nullable
        String displayName();

        @Nullable
        String email();

        @Nullable
        Set<String> groups();

        @Nullable
        Map<String, Object> attributes();

        @Nullable
        String usernameSignature();
    }
}
