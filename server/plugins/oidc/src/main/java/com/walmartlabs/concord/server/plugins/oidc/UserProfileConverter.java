package com.walmartlabs.concord.server.plugins.oidc;

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
