package com.walmartlabs.concord.client;

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

import com.google.gson.Gson;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.*;
import com.squareup.okhttp.internal.Util;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.Pair;
import com.walmartlabs.concord.auth.Authentication;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

public final class ClientUtils {

    private static final Logger log = LoggerFactory.getLogger(ClientUtils.class);

    private static final MediaType APPLICATION_OCTET_STREAM_TYPE = MediaType.parse("application/octet-stream");
    private static final MediaType APPLICATION_JSON_TYPE = MediaType.parse("application/json");
    private static final MediaType TEXT_PLAIN_TYPE = MediaType.parse("text/plain");

    private static final Gson gson = new Gson();

    public static <T> T withRetry(int retryCount, long retryInterval, Callable<T> c) throws ApiException {
        Exception exception = null;
        int tryCount = 0;
        while (!Thread.currentThread().isInterrupted() && tryCount < retryCount + 1) {
            try {
                return c.call();
            } catch (ApiException e) {
                exception = e;

                if (e.getCode() >= 400 && e.getCode() < 500) {
                    break;
                }
                log.warn("call error: '{}'", getErrorMessage(e));
            } catch (Exception e) {
                exception = e;
                log.error("call error", e);
            }
            log.info("retry after {} sec", retryInterval / 1000);
            sleep(retryInterval);
            tryCount++;
        }

        if (exception instanceof ApiException) {
            throw (ApiException) exception;
        }

        throw new ApiException(exception);
    }

    /**
     * Returns a value of the specified header.
     * Only the first value is returned.
     * The header's {@code name} is case-insensitive.
     */
    public static String getHeader(String name, ApiResponse<?> resp) {
        Map<String, List<String>> headers = resp.getHeaders();
        if (headers == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (!e.getKey().equalsIgnoreCase(name)) {
                continue;
            }

            List<String> values = e.getValue();

            if (values == null || values.isEmpty()) {
                return null;
            }

            return values.get(0);
        }

        return null;
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Object data) throws ApiException {
        return postData(client, path, data, null);
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Object data, Type returnType) throws ApiException {
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Content-Type", "application/octet-stream");

        return postData(client, path, data, headerParams, returnType);
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Object data, Map<String, String> headerParams, Type returnType) throws ApiException {
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        Call call = client.buildCall(path, "POST", new ArrayList<>(), new ArrayList<>(),
                data, headerParams, new HashMap<>(), authNames, null);
        return client.execute(call, returnType);
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Map<String, Object> data, Class<T> returnType) throws ApiException {
        List<Pair> queryParams = new ArrayList<>();
        Map<String, String> headerParams = new HashMap<>();

        Map<String, Authentication> auths = client.getAuthentications();
        for (Map.Entry<String, Authentication> e : auths.entrySet()) {
            Authentication a = e.getValue();
            a.applyToParams(queryParams, headerParams);
        }

        String url = client.buildUrl(path, queryParams, null);
        Request.Builder b = new Request.Builder().url(url);
        client.processHeaderParams(headerParams, b);

        RequestBody body = createMultipartBody(data).build();
        Request request = b.method("POST", body).build();

        OkHttpClient ok = client.getHttpClient();
        Call c = ok.newCall(request);

        return client.execute(c, returnType);
    }

    public static MultipartBuilder createMultipartBody(Map<String, Object> data) {
        MultipartBuilder b = new MultipartBuilder().type(MultipartBuilder.FORM);
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof InputStream) {
                b.addFormDataPart(k, null, new InputStreamRequestBody((InputStream) v));
            } else if (v instanceof byte[]) {
                b.addFormDataPart(k, null, RequestBody.create(APPLICATION_OCTET_STREAM_TYPE, (byte[]) v));
            } else if (v instanceof String) {
                b.addFormDataPart(k, (String) v);
            } else if (v instanceof Path) {
                b.addFormDataPart(k, null, new PathRequestBody((Path) v));
            } else if (v instanceof Map) {
                String json = gson.toJson(v);
                b.addFormDataPart(k, null, RequestBody.create(APPLICATION_JSON_TYPE, json));
            } else if (v instanceof Boolean) {
                b.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN_TYPE, v.toString()));
            } else if (v instanceof String[]) {
                b.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN_TYPE, String.join(",", (String[]) v)));
            } else if (v instanceof UUID) {
                b.addFormDataPart(k, v.toString());
            } else if (v instanceof Enum<?>) {
                b.addFormDataPart(k, ((Enum<?>)v).name());
            } else {
                throw new IllegalArgumentException("Unknown input type: " + k + "=" + v + (v != null ? " (" + v.getClass() + ")" : ""));
            }
        }
        return b;
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String getErrorMessage(ApiException e) {
        String error = e.getMessage();
        if (e.getResponseBody() != null && !e.getResponseBody().isEmpty()) {
            error += ": " + e.getResponseBody();
        }
        return error;
    }

    public static final class InputStreamRequestBody extends RequestBody {

        private final InputStream in;

        public InputStreamRequestBody(InputStream in) {
            this.in = in;
        }

        @Override
        public com.squareup.okhttp.MediaType contentType() {
            return APPLICATION_OCTET_STREAM_TYPE;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.writeAll(Okio.source(in));
        }
    }

    public static class PathRequestBody extends RequestBody {

        private final Path path;

        public PathRequestBody(Path path) {
            this.path = path;
        }

        @Override
        public MediaType contentType() {
            return APPLICATION_OCTET_STREAM_TYPE;
        }

        @Override
        public long contentLength() throws IOException {
            return Files.size(path);
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            Source source = null;
            try {
                source = Okio.source(path);
                sink.writeAll(source);
            } finally {
                Util.closeQuietly(source);
            }
        }
    }

    private ClientUtils() {
    }
}
