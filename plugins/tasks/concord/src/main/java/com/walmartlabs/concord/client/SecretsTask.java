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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Named("concordSecrets")
public class SecretsTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SecretsTask.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    public static final String ACTION_KEY = "action";
    public static final String NEW_STORE_PASSWORD_KEY = "newStorePassword"; // NOSONAR
    public static final String RESULT_KEY = "result";
    public static final String IGNORE_ERRORS_KEY = "ignoreErrors";
    public static final String CREATE_IF_MISSING_KEY = "createIfMissing";

    private final ApiClientFactory clientFactory;

    @Inject
    public SecretsTask(ApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);

        Result result;
        switch (action) {
            case GETASSTRING: {
                result = getAsString(ctx);
                break;
            }
            case CREATE: {
                result = create(ctx);
                break;
            }
            case UPDATE: {
                result = update(ctx);
                break;
            }
            case DELETE: {
                result = delete(ctx);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + action);
            }
        }

        ctx.setVariable(RESULT_KEY, result.toMap());
    }

    private Result getAsString(Context ctx) {
        Map<String, Object> params = new HashMap<>();

        String orgName = assertOrgName(ctx);
        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);

        // TODO allow empty multipart requests?
        params.put(Constants.Multipart.NAME, secretName);

        addIfPresent(params, ctx, Constants.Multipart.STORE_PASSWORD);

        try {
            ApiResponse<String> r = postWithRetry(ctx, "/api/v1/org/" + orgName + "/secret/" + secretName + "/data", params);

            String data = r.getData();
            if (data == null) {
                return Result.ok();
            }

            return Result.ok(r.getData());
        } catch (ApiException e) {
            return handleErrors(ctx, secretName, e);
        }
    }

    private static Map<String, Object> makeCreateParams(Context ctx) {
        Map<String, Object> m = new HashMap<>();

        String orgName = assertOrgName(ctx);
        m.put(Constants.Multipart.ORG_NAME, orgName);

        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);
        m.put(Constants.Multipart.NAME, secretName);

        SecretEntry.TypeEnum secretType = getSecretType(ctx);
        m.put(Constants.Multipart.TYPE, secretType.toString());

        switch (secretType) {
            case DATA:
                m.put(Constants.Multipart.DATA, ContextUtils.assertVariable(ctx, Constants.Multipart.DATA, Object.class));
                break;
            case KEY_PAIR:
                m.put(Constants.Multipart.PUBLIC, ContextUtils.assertString(ctx, Constants.Multipart.PUBLIC));
                m.put(Constants.Multipart.PRIVATE, ContextUtils.assertString(ctx, Constants.Multipart.PRIVATE));
                break;
            case USERNAME_PASSWORD:
                m.put(Constants.Multipart.USERNAME, ContextUtils.assertString(ctx, Constants.Multipart.USERNAME));
                m.put(Constants.Multipart.PASSWORD, ContextUtils.assertString(ctx, Constants.Multipart.PASSWORD));
                break;
        }

        addIfPresent(m, ctx, Constants.Multipart.STORE_PASSWORD);
        addIfPresent(m, ctx, Constants.Multipart.GENERATE_PASSWORD);
        addIfPresent(m, ctx, Constants.Multipart.VISIBILITY);
        addIfPresent(m, ctx, Constants.Multipart.PROJECT_NAME);

        return m;
    }

    private Result create(Context ctx) throws Exception {
        Map<String, Object> params = makeCreateParams(ctx);
        return create(ctx, params);
    }

    private Result create(Context ctx, Map<String, Object> params) throws Exception {
        String orgName = (String) params.get(Constants.Multipart.ORG_NAME);
        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);

        ApiResponse<SecretOperationResponse> r = post(ctx, "/api/v1/org/" + orgName + "/secret", params, SecretOperationResponse.class);
        if (r.getStatusCode() >= 400) {
            return handleErrors(ctx, secretName, r.getStatusCode(), r.getData().toString());
        }

        log.info("New secret was successfully created: {}", params.get(Constants.Multipart.NAME));
        return Result.ok();
    }

    private Result update(Context ctx) throws Exception {
        String orgName = assertOrgName(ctx);
        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);

        String newData = null;
        Object data = ContextUtils.getVariable(ctx, Constants.Multipart.DATA, null);
        if (data != null) {
            if (data instanceof String) {
                String s = data.toString();
                newData = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
            } else if (data instanceof byte[]) {
                byte[] ab = (byte[]) data;
                newData = Base64.getEncoder().encodeToString(ab);
            } else {
                throw new RuntimeException("Unsupported '" + Constants.Multipart.DATA + "' type: " + data.getClass() + ". " +
                        "Only string values and byte arrays are allowed.");
            }
        }

        String storePassword = ContextUtils.getString(ctx, Constants.Multipart.STORE_PASSWORD);
        String newStorePassword = ContextUtils.getString(ctx, NEW_STORE_PASSWORD_KEY);

        if (newData == null && newStorePassword == null) {
            log.warn("Not updating anything since nothing has changed.");
            return Result.ok();
        }

        try {
            SecretsApi api = new SecretsApi(clientFactory.create(ctx));
            api.update(orgName, secretName, new SecretUpdateRequest()
                    .setData(newData)
                    .setStorePassword(storePassword)
                    .setNewStorePassword(newStorePassword));

            log.info("The secret was successfully updated: {}", secretName);
            return Result.ok();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                boolean createIfMissing = ContextUtils.getBoolean(ctx, CREATE_IF_MISSING_KEY, false);
                if (createIfMissing) {
                    return create(ctx);
                }
            }

            return handleErrors(ctx, secretName, e.getCode(), e.getResponseBody());
        }
    }

    private Result delete(Context ctx) {
        String secretName = ContextUtils.getString(ctx, Constants.Multipart.NAME);

        try {
            SecretsApi api = new SecretsApi(clientFactory.create(ctx));
            GenericOperationResult result = api.delete(assertOrgName(ctx), secretName);
            switch (result.getResult()) {
                case DELETED: {
                    log.info("The secret was successfully deleted: {}", secretName);
                    return Result.ok();
                }
                case NOT_FOUND: {
                    log.warn("Secret not found: {}", secretName);
                    return Result.notFound();
                }
                default: {
                    return Result.invalidRequest();
                }
            }
        } catch (ApiException e) {
            return handleErrors(ctx, secretName, e.getCode(), e.getResponseBody());
        }
    }

    private <T> ApiResponse<T> post(Context ctx, String path, Map<String, Object> params, Class<T> type) throws ApiException {
        ApiClient c = clientFactory.create(ctx);
        return ClientUtils.postData(c, path, params, type);
    }

    private ApiResponse<String> postWithRetry(Context ctx, String path, Map<String, Object> params) throws ApiException {
        ApiClient c = clientFactory.create(ctx);
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL,
                () -> ClientUtils.postData(c, path, params, String.class));
    }

    @SuppressWarnings("unchecked")
    private static String assertOrgName(Context ctx) {
        String n = ContextUtils.getString(ctx, Constants.Multipart.ORG_NAME);
        if (n != null) {
            return n;
        }

        Map<String, Object> m = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
        return Optional.ofNullable(m)
                .map(p -> (String) p.get("orgName"))
                .orElseThrow(() -> new IllegalArgumentException("Organization name not specified"));
    }

    private static Action getAction(Context ctx) {
        String action = ContextUtils.getString(ctx, ACTION_KEY, Action.GETASSTRING.toString());
        try {
            return Action.valueOf(action.toUpperCase());
        } catch (Exception e) {
            String message = String.format("Invalid argument '%s', allowed values are: 'getAsString' (default), 'create', 'delete' and 'replace'", action);
            throw new IllegalArgumentException(message);
        }
    }

    private static SecretEntry.TypeEnum getSecretType(Context ctx) {
        String type = ContextUtils.getString(ctx, Constants.Multipart.TYPE, SecretEntry.TypeEnum.DATA.toString());
        try {
            return SecretEntry.TypeEnum.valueOf(type.toUpperCase());
        } catch (Exception e) {
            String message = String.format("Invalid argument '%s', allowed values are: 'data' (default), 'key_pair' and 'username_password'", type);
            throw new IllegalArgumentException(message);
        }
    }

    private static void addIfPresent(Map<String, Object> m, Context ctx, String key) {
        addIfPresent(m, ctx, key, null);
    }

    private static void addIfPresent(Map<String, Object> m, Context ctx, String key, Object defaultValue) {
        Object v = ctx.getVariable(key);
        if (v != null) {
            m.put(key, v);
        }

        if (defaultValue != null) {
            m.put(key, v);
        }
    }

    private static Result handleErrors(Context ctx, String secretName, ApiException e) {
        return handleErrors(ctx, secretName, e.getCode(), e.getResponseBody());
    }

    private static Result handleErrors(Context ctx, String secretName, int code, String responseBody) {
        boolean ignoreErrors = ContextUtils.getBoolean(ctx, IGNORE_ERRORS_KEY, false);

        if (code == 401) {
            if (ignoreErrors) {
                log.warn("Access denied to secret '{}' ", secretName);
                return Result.accessDenied();
            }
            throw new RuntimeException("Access denied: " + secretName);
        } else if (code == 404) {
            if (ignoreErrors) {
                log.warn("Secret '{}' not found", secretName);
                return Result.notFound();
            }
            throw new RuntimeException("Secret not found: " + secretName);
        } else if (code >= 400 && code < 500 && ignoreErrors) {
            log.warn("Invalid request for secret '{}': {}", secretName, code);
            return Result.invalidRequest();
        }

        throw new RuntimeException("Error while requesting the secret '" + secretName + "': (code=" + code + "): " + responseBody);
    }

    public enum Action {
        GETASSTRING,
        CREATE,
        UPDATE,
        DELETE
    }

    public enum Status {
        OK,
        NOT_FOUND,
        INVALID_REQUEST,
        ACCESS_DENIED
    }

    public static class Result implements Serializable {

        private static Result ok() {
            return new Result(true, Status.OK, null);
        }

        private static Result ok(String data) {
            return new Result(true, Status.OK, data);
        }

        private static Result notFound() {
            return new Result(false, Status.NOT_FOUND, null);
        }

        private static Result invalidRequest() {
            return new Result(false, Status.INVALID_REQUEST, null);
        }

        private static Result accessDenied() {
            return new Result(false, Status.ACCESS_DENIED, null);
        }

        private final boolean ok;
        private final Status status;
        private final String data;

        private Result(boolean ok, Status status, String data) {
            this.ok = ok;
            this.status = status;
            this.data = data;
        }

        public boolean isOk() {
            return ok;
        }

        public Status getStatus() {
            return status;
        }

        public String getData() {
            return data;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("ok", this.ok);
            m.put("status", this.status.toString());
            m.put("data", this.data);
            return m;
        }
    }
}
