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
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.AbstractContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.walmartlabs.concord.plugins.http.HttpTask.HttpTaskConstant.*;

/**
 * Utility class which contains helper methods for {@link HttpTask}
 */
public final class HttpTaskUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpTaskUtils.class);

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
            String token = String.valueOf(basicAuthParams.get(TOKEN_KEY));
            try {
                Base64.getDecoder().decode(token);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid auth token value. Expected a base64 encoded string, got: " + token);
            }
            return token;
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
    private static String getBasicAuthorization(String username, String password) {
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
    @SuppressWarnings("unchecked")
    static HttpEntity getHttpEntity(Object body, RequestType requestType) throws Exception {
        if ((RequestType.FILE == requestType) && (body instanceof String)) {
            String filePath = (String) body;
            File newFile = new File(filePath);

            if (!newFile.exists()) {
                throw new FileNotFoundException("File: " + filePath + " not found");
            }

            return new FileEntity(newFile, ContentType.APPLICATION_OCTET_STREAM);

        } else if ((RequestType.FORM == requestType) && (body instanceof Map)) {
            List<NameValuePair> params = new ArrayList<>();

            Map<String, Object> bodyParams = (Map<String, Object>) body;
            bodyParams.forEach((k, v) -> {
                if (v instanceof Collection) {
                    ((Collection<Object>) v).forEach(item -> params.add(new BasicNameValuePair(k, item.toString())));
                } else {
                    params.add(new BasicNameValuePair(k, v.toString()));
                }
            });

            return new UrlEncodedFormEntity(params);
        } else if ((RequestType.FORMDATA == requestType) && body instanceof Map) {
            return createMultipartBody((Map<String, Object>) body);
        } else if ((RequestType.JSON == requestType)) {
            if (body instanceof String) {
                String strBody = (String) body;
                if (isValidJSON(strBody)) {
                    return new StringEntity(strBody, StandardCharsets.UTF_8);
                }
            } else {
                return new StringEntity(new ObjectMapper().writeValueAsString(body), StandardCharsets.UTF_8);
            }
        } else if ((RequestType.STRING == requestType) && (body instanceof String)) {
            return new StringEntity((String) body, StandardCharsets.UTF_8);
        }

        throw new IllegalArgumentException("'" + REQUEST_KEY + ": " + requestType.toString().toLowerCase() + "' is not compatible with '" + BODY_KEY + "'");
    }

    @SuppressWarnings("unchecked")
    private static HttpEntity createMultipartBody(Map<String, Object> body) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        for (Map.Entry<String, Object> e : body.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();

            if (v instanceof String value) {
                if (value.startsWith("@")) {
                    builder.addPart(k, createContentBody(value, ContentType.APPLICATION_OCTET_STREAM));
                } else {
                    builder.addPart(k, createContentBody(value, ContentType.TEXT_PLAIN));
                }
            } else if (v instanceof Map) {
                Map<String, String> field = (Map<String, String>) v;
                String type = field.get("type");
                String data = field.get("data");

                if (data == null) {
                    throw new IllegalArgumentException("field -> " + k + " missing request data");
                }

                builder.addPart(k, createContentBody(data, ContentType.parse(type)));
            } else if (v instanceof Number) {
                builder.addPart(k, createContentBody(String.valueOf(v), ContentType.TEXT_PLAIN));
            } else {
                log.warn("Skipping value for key '{}': unsupported type {}. Expected String, Number, or Map<String, String>.", k, v == null ? "null" : v.getClass().getSimpleName());
            }
        }

        return builder.build();

    }

    private static AbstractContentBody createContentBody(String value, ContentType type) {
        if (ContentType.APPLICATION_OCTET_STREAM.getMimeType().equals(type.getMimeType())) {
            String filePath = value.substring(1);
            File file = new File(filePath);
            if (!file.exists()) {
                throw new IllegalArgumentException("file -> " + filePath + " does not exists");
            }

            return new FileBody(file);
        }

        return new StringBody(value, type);
    }

    /**
     * Method to validate the json string
     *
     * @param body json string
     * @return true if json is valid
     */
    private static boolean isValidJSON(String body) {
        ObjectMapper om = new ObjectMapper();
        try {
            om.readTree(body);
        } catch (IOException e) {
            log.error("invalid json body '{}': {}", body, e.getMessage());
            return false;
        }

        return true;
    }

    private HttpTaskUtils() {
    }
}
