package com.walmartlabs.concord.client2.impl;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.client2.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ResponseBodyHandler {

    @SuppressWarnings("unchecked")
    public static <T> T handle(ObjectMapper objectMapper,
                              HttpResponse<InputStream> response,
                              TypeReference<T> returnTypeRef) throws IOException, ApiException {
        if (response == null) {
            return null;
        }

        Type returnType = returnTypeRef.getType();
        InputStream is = response.body();
        if (is == null) {
            return null;
        }

        try {
            if (returnType.equals(byte[].class)) {
                return (T)is.readAllBytes();
            } else if (returnType.equals(InputStream.class)) {
                return (T)is;
            }

            String contentType = response.headers().firstValue("Content-Type").orElse("application/json");
            if (isJsonMime(contentType)) {
                return objectMapper.readValue(is, returnTypeRef);
            } else if (returnType.equals(String.class)) {
                return (T) toString(is, charset(response));
            } else {
                throw new ApiException(
                        "Content type \"" + contentType + "\" is not supported for type: " + returnType,
                        response.statusCode(),
                        response.headers(),
                        "skipped");
            }
        } finally {
            if (!returnType.equals(InputStream.class)) {
                is.close();
            }
        }
    }

    private static boolean isJsonMime(String mime) {
        String jsonMime = "(?i)^(application/json|[^;/ \t]+/[^;/ \t]+[+]json)[ \t]*(;.*)?$";
        return mime != null && (mime.matches(jsonMime) || mime.equals("*/*"));
    }

    private static String toString(InputStream input, Charset charset) throws IOException {
        return new String(input.readAllBytes(), charset);
    }

    private static Charset charset(HttpResponse<InputStream> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse(null);
        if (contentType == null) {
            return StandardCharsets.UTF_8;
        }

        return parseCharset(contentType, StandardCharsets.UTF_8);
    }

    // TODO: super simple
    private static Charset parseCharset(String contentTypeHeader, Charset defaultCharset) {
        Pattern pattern = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(contentTypeHeader);

        if (matcher.find()) {
            return Charset.forName(matcher.group(1));
        }

        return defaultCharset;
    }

    private ResponseBodyHandler() {
    }
}
