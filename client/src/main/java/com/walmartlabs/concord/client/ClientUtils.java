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
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.Pair;
import com.walmartlabs.concord.auth.Authentication;
import okio.BufferedSink;
import okio.Okio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
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

                log.error("call error: '{}'", getErrorMessage(e));

                if (e.getCode() >= 400 && e.getCode() < 500) {
                    break;
                }
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

    public static String getHeader(String name, ApiResponse<?> resp) {
        return resp.getHeaders().get(name).get(0);
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Object data) throws ApiException {
        return postData(client, path, data, null);
    }

    public static <T> ApiResponse<T> postData(ApiClient client, String path, Object data, Type returnType) throws ApiException {
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("Content-Type", "application/octet-stream");

        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        Call c = client.buildCall(path, "POST", new ArrayList<>(), new ArrayList<>(),
                data, headerParams, new HashMap<>(), authNames, null);
        return client.execute(c, returnType);
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

        MultipartBuilder mpb = new MultipartBuilder().type(MultipartBuilder.FORM);
        for (Map.Entry<String, Object> e : data.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (v instanceof InputStream) {
                mpb.addFormDataPart(k, null, new InputStreamRequestBody((InputStream) v));
            } else if (v instanceof byte[]) {
                mpb.addFormDataPart(k, null, RequestBody.create(APPLICATION_OCTET_STREAM_TYPE, (byte[]) v));
            } else if (v instanceof String) {
                mpb.addFormDataPart(k, (String) v);
            } else if (v instanceof Map) {
                String json = gson.toJson(v);
                mpb.addFormDataPart(k, null, RequestBody.create(APPLICATION_JSON_TYPE, json));
            } else if (v instanceof Boolean) {
                mpb.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN_TYPE, v.toString()));
            } else if (v instanceof String[]) {
                mpb.addFormDataPart(k, null, RequestBody.create(TEXT_PLAIN_TYPE, String.join(",", (String[]) v)));
            } else if (v instanceof UUID) {
                mpb.addFormDataPart(k, v.toString());
            } else {
                throw new IllegalArgumentException("Unknown input type: " + v);
            }
        }

        RequestBody body = mpb.build();
        Request request = b.method("POST", body).build();

        OkHttpClient ok = client.getHttpClient();
        Call c = ok.newCall(request);

        return client.execute(c, returnType);
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

    private ClientUtils() {
    }
}
