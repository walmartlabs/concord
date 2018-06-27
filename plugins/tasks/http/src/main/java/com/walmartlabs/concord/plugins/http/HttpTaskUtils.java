package com.walmartlabs.concord.plugins.http;

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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.plugins.http.HttpTask.RequestType;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import static com.walmartlabs.concord.plugins.http.HttpTask.HttpTaskConstant.*;

/**
 * Utility class which contains helper methods for {@link HttpTask}
 */
public final class HttpTaskUtils {
    /**
     * Method to get the basic authorization header entry using the basic authorization params provided in the concord.yml
     * file. It will use either the token key or the username and password key to generate the header entry. If token key
     * is provided then it must be in Base64 encoded string just like curl
     *
     * @param basicAuthParams use to generate the basic authorization header entry
     * @return String ("Basic BASE64_TOKEN")
     */
    static String getBasicAuthorization(Map<String, String> basicAuthParams) {
        if (basicAuthParams.get(TOKEN_KEY) != null) {
            return String.valueOf(basicAuthParams.get(TOKEN_KEY));
        } else {
            return getBasicAuthorization(basicAuthParams.get(USERNAME_KEY), basicAuthParams.get(PASSWORD_KEY));
        }
    }

    /**
     * Method to encode the given username and password
     *
     * @param username username
     * @param password password
     * @return Base64 encoded {@link String}
     */
    static String getBasicAuthorization(String username, String password) {
        return Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
    }

    /**
     * Method to get the HttpEntity using the request type.
     *
     * @param body        Contain complex object or relative path as string
     * @param requestType type of request
     * @return FileEntity for RequestType.FILE with ContentType.APPLICATION_OCTET_STREAM otherwise return {@link StringEntity}
     * @throws Exception exception
     */
    static HttpEntity getHttpEntity(Object body, RequestType requestType) throws Exception {
        if ((RequestType.FILE == requestType) && (body instanceof String)) {
            String filePath = (String) body;
            File newFile = new File(filePath);

            if (!newFile.exists()) {
                throw new FileNotFoundException("File: " + filePath + " not found");
            }

            return new FileEntity(newFile, ContentType.APPLICATION_OCTET_STREAM);

        } else if ((RequestType.JSON == requestType)) {
            if (body instanceof String) {
                String strBody = (String) body;
                if (isValidJSON(strBody)) {
                    return new StringEntity(strBody);
                }
            } else if (body instanceof Map) {
                return new StringEntity(new ObjectMapper().writeValueAsString(body));
            }
        } else if ((RequestType.STRING == requestType) && (body instanceof String)) {
            return new StringEntity((String) body);
        }

        throw new IllegalArgumentException("'" + REQUEST_KEY + ": " + requestType.toString().toLowerCase() + "' is not compatible with '" + BODY_KEY + "'");
    }

    /**
     * Method to validate the json string
     *
     * @param body json string
     * @return true if json is valid
     */
    public static boolean isValidJSON(String body) {
        ObjectMapper om = new ObjectMapper();
        try {
            om.readTree(body);
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    private HttpTaskUtils() {
    }
}
