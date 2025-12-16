package com.walmartlabs.concord.server.plugins.oidc;

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class UserProfileConverter {

    public static UserProfile convert(ObjectMapper objectMapper, String json, String accessToken) throws JsonProcessingException {
        var userInfo = objectMapper.readTree(json);
        var id = userInfo.get("sub").asText();
        var email = userInfo.get("email").asText();
        var displayName = userInfo.get("name").asText(email);
        return new UserProfile(id, email, displayName, accessToken, objectMapper.convertValue(userInfo, new TypeReference<>() {
        }));
    }

    private UserProfileConverter() {
    }
}
