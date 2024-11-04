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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.sdk.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.walmartlabs.concord.client.SecretsTaskParams.*;

public class SecretsTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(SecretsTaskCommon.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    private final ApiClient apiClient;
    private final String defaultOrg;
    private final boolean dryRunMode;

    public SecretsTaskCommon(ApiClient apiClient, String defaultOrg) {
        this(apiClient, defaultOrg, false);
    }

    public SecretsTaskCommon(ApiClient apiClient, String defaultOrg, boolean dryRunMode) {
        this.apiClient = apiClient;
        this.defaultOrg = defaultOrg;
        this.dryRunMode = dryRunMode;
    }

    public TaskResult.SimpleResult execute(SecretsTaskParams in) throws Exception {
        Action action = in.action();

        TaskResult.SimpleResult result;
        switch (action) {
            case GETASSTRING: {
                result = getAsString((AsStringParams)in);
                break;
            }
            case CREATE: {
                result = create((CreateParams)in);
                break;
            }
            case UPDATE: {
                result = update((UpdateParams)in);
                break;
            }
            case DELETE: {
                result = delete(in);
                break;
            }
            default: {
                throw new IllegalArgumentException("Unsupported action type: " + action);
            }
        }

        return result;
    }

    private TaskResult.SimpleResult getAsString(AsStringParams in) {
        String orgName = in.orgName(defaultOrg);
        String secretName = in.secretName();

        // TODO allow empty multipart requests?
        Map<String, Object> params = new HashMap<>();
        params.put(Constants.Multipart.NAME, secretName);

        addIfPresent(params, Constants.Multipart.STORE_PASSWORD, in.storePassword());

        SecretsApi api = new SecretsApi(apiClient);
        try (InputStream is = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> api.getSecretData(orgName, secretName, params))) {
            String data = new String(is.readAllBytes());
            return Result.ok(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ApiException e) {
            return handleErrors(in, secretName, e);
        }
    }

    private Map<String, Object> makeCreateParams(CreateParams in) {
        Map<String, Object> m = new HashMap<>();

        String orgName = in.orgName(defaultOrg);
        m.put(Constants.Multipart.ORG_NAME, orgName);

        String secretName = in.secretName();
        m.put(Constants.Multipart.NAME, secretName);

        SecretEntryV2.TypeEnum secretType = in.secretType();
        m.put(Constants.Multipart.TYPE, secretType.toString());

        switch (secretType) {
            case DATA:
                m.put(Constants.Multipart.DATA, in.data());
                break;
            case KEY_PAIR:
                m.put(Constants.Multipart.PUBLIC, in.publicKey());
                m.put(Constants.Multipart.PRIVATE, in.privateKey());
                break;
            case USERNAME_PASSWORD:
                m.put(Constants.Multipart.USERNAME, in.userName());
                m.put(Constants.Multipart.PASSWORD, in.password());
                break;
        }

        addIfPresent(m, Constants.Multipart.STORE_PASSWORD, in.storePassword());
        addIfPresent(m, Constants.Multipart.GENERATE_PASSWORD, in.generatePassword());
        addIfPresent(m, Constants.Multipart.VISIBILITY, in.visibility());
        addIfPresent(m, Constants.Multipart.PROJECT_NAME, in.projectName());

        return m;
    }

    private TaskResult.SimpleResult create(CreateParams in) throws Exception {
        Map<String, Object> params = makeCreateParams(in);
        return create(in, params);
    }

    private TaskResult.SimpleResult create(CreateParams in, Map<String, Object> params) throws Exception {
        String orgName = in.orgName(defaultOrg);

        if (dryRunMode) {
            log.info("Dry-run mode enabled: Skipping creation of secret '{}'", params.get(Constants.Multipart.NAME));
            return Result.ok();
        }

        new SecretsApi(apiClient).createSecret(orgName, params);

        log.info("New secret was successfully created: {}", params.get(Constants.Multipart.NAME));
        return Result.ok();
    }

    private TaskResult.SimpleResult update(UpdateParams in) throws Exception {

        String newData = null;
        Object data = in.data();

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

        String storePassword = in.storePassword();
        String newStorePassword = in.newStorePassword();

        if (newData == null && newStorePassword == null) {
            log.warn("Not updating anything since nothing has changed.");
            return Result.ok();
        }

        String orgName = in.orgName(defaultOrg);
        String secretName = in.secretName();
        List<String> projectNames = in.projectNames();
        List<UUID> projectIds = in.projectIds();

        try {
            Map<String,Object> params = new HashMap<>();
            addIfPresent(params, Constants.Multipart.DATA, data);
            addIfPresent(params, Constants.Multipart.STORE_PASSWORD, storePassword);
            addIfPresent(params, Constants.Multipart.NEW_STORE_PASSWORD, newStorePassword);
            addIfPresent(params, Constants.Multipart.PROJECT_IDS, projectIds);
            addIfPresent(params, Constants.Multipart.PROJECT_NAMES, projectNames);
            if(data != null){
                addIfPresent(params, Constants.Multipart.TYPE, in.secretType());
            }

            if (dryRunMode) {
                log.info("Dry-run mode enabled: Skipping updating of secret '{}'", secretName);
                return Result.ok();
            }

            new SecretsV2Api(apiClient).updateSecret(orgName, secretName, params);

            log.info("The secret was successfully updated: {}", secretName);
            return Result.ok();
        } catch (ApiException e) {
            if (e.getCode() == 404) {
                boolean createIfMissing = in.createIfMissing();
                if (createIfMissing) {
                    return create(in);
                }
            }

            return handleErrors(in, secretName, e.getCode(), e.getResponseBody());
        }
    }

    private TaskResult.SimpleResult delete(SecretsTaskParams in) {
        String secretName = in.secretName();

        if (dryRunMode) {
            log.info("Dry-run mode enabled: Skipping deleting of secret '{}'", secretName);
            // TODO: check secret exists?
            return Result.ok();
        }

        try {
            SecretsApi api = new SecretsApi(apiClient);
            GenericOperationResult result = api.delete(in.orgName(defaultOrg), secretName);
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
            return handleErrors(in, secretName, e.getCode(), e.getResponseBody());
        }
    }

    private static void addIfPresent(Map<String, Object> m, String key, Object value) {
        if (value != null) {
            m.put(key, value);
        }
    }

    private static TaskResult.SimpleResult handleErrors(SecretsTaskParams in, String secretName, ApiException e) {
        return handleErrors(in, secretName, e.getCode(), e.getResponseBody());
    }

    private static TaskResult.SimpleResult handleErrors(SecretsTaskParams in, String secretName, int code, String responseBody) {
        boolean ignoreErrors = in.ignoreErrors();

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

    public enum Status {
        OK,
        NOT_FOUND,
        INVALID_REQUEST,
        ACCESS_DENIED
    }

    static class Result {

        private static TaskResult.SimpleResult ok() {
            return result(true, Status.OK, null);
        }

        private static TaskResult.SimpleResult ok(String data) {
            return result(true, Status.OK, data);
        }

        private static TaskResult.SimpleResult notFound() {
            return result(false, Status.NOT_FOUND, null);
        }

        private static TaskResult.SimpleResult invalidRequest() {
            return result(false, Status.INVALID_REQUEST, null);
        }

        private static TaskResult.SimpleResult accessDenied() {
            return result(false, Status.ACCESS_DENIED, null);
        }

        private static TaskResult.SimpleResult result(boolean ok, Status status, String data) {
            return TaskResult.of(ok)
                    .value("status", status.toString())
                    .value("data", data);
        }
    }
}
