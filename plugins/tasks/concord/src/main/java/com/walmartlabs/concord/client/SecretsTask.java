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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Named("secrets")
public class SecretsTask implements Task {
    private static final Logger log = LoggerFactory.getLogger(SecretsTask.class);
    private static final String NEW_STORE_PASSWORD = "newStorePassword";
    private static final String SKIP_VALIDATION = "skipValidation";

    private enum Action {GET, VALIDATE, CREATE, REPLACE, DELETE}
    private enum ValidationStatus {OK, MISSING, INVALID, NOT_OWNER}
    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;
    private static final String ACTION_KEY = "action";

    private final ApiClientFactory clientFactory;

    @Inject
    public SecretsTask(ApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public void execute(Context ctx) throws Exception {
        Action action = getAction(ctx);
        switch (action) {
            case GET: {
                String result = get(ctx);
                ctx.setVariable("result", result);
                return;
            }
            case VALIDATE: {
                ValidationStatus result = validate(ctx);
                ctx.setVariable("result", result.toString());
                return;
            }
            case CREATE: {
                Map result = create(ctx);
                ctx.setVariable("result", result);
                return;
            }
            case REPLACE: {
                ValidationStatus result = update(ctx);
                ctx.setVariable("result", result.toString());
                return;
            }
            case DELETE:
                delete(ctx);
        }
    }

    private String get(Context ctx) throws ApiException {
        Map<String, Object> params = new HashMap<>();
        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);
        cpCtx(ctx, params, Constants.Multipart.STORE_PASSWORD);
        params.put(Constants.Multipart.TYPE, ContextUtils.getString(ctx, Constants.Multipart.TYPE, "data"));

        try {
            ApiResponse<String> r = postWithRetry(ctx, "/api/v1/org/" + assertOrgName(ctx, null) + "/secret/" + secretName + "/data", params);
            if (r.getData() == null) {
                return "";
            } else {
                return r.getData();
            }
        } catch (ApiException e) {
            if (e.toString().contains("Not Found")) {
                log.warn("Secret [" + secretName + "] not found.");
                return "";
            } else {
                throw e;
            }
        }
    }

    private ValidationStatus validate(Context ctx) throws ApiException {
        Map<String, Object> params = new HashMap<>();
        String secretName = ContextUtils.assertString(ctx, Constants.Multipart.NAME);
        cpCtx(ctx, params, Constants.Multipart.STORE_PASSWORD);
        params.put(Constants.Multipart.TYPE, ContextUtils.getString(ctx, Constants.Multipart.TYPE, "DATA"));

        try {
            ApiResponse<String> r = postWithRetry(ctx, "/api/v1/org/" + assertOrgName(ctx, null) + "/secret/" + secretName + "/data", params);
            return ValidationStatus.OK;
        } catch (ApiException e) {
            log.error(e.getCode() + " " + e.getResponseBody() + " " + e);
            switch (e.getCode()) {
                case 400:
                    return ValidationStatus.INVALID;
                case 401:
                    return ValidationStatus.NOT_OWNER;
                case 404:
                    return ValidationStatus.MISSING;
                default:
                    throw e;
            }
        }
    }



    private Map create(Context ctx) throws ApiException {
        SecretEntry.TypeEnum secretType = getSecretType(ctx);
        Map<String, Object> params = new HashMap<>();
        String org = assertOrgName(ctx, null);
        ContextUtils.assertString(ctx, Constants.Multipart.NAME);

        switch (secretType) {
            case DATA:
                params.put(Constants.Multipart.DATA, ContextUtils.assertVariable(ctx, Constants.Multipart.DATA, Object.class));
                break;
            case KEY_PAIR:
                params.put(Constants.Multipart.PUBLIC, ContextUtils.assertString(ctx, Constants.Multipart.PUBLIC));
                params.put(Constants.Multipart.PRIVATE, ContextUtils.assertString(ctx, Constants.Multipart.PRIVATE));
                break;
            case USERNAME_PASSWORD:
                params.put(Constants.Multipart.USERNAME, ContextUtils.assertString(ctx, Constants.Multipart.USERNAME));
                params.put(Constants.Multipart.PASSWORD, ContextUtils.assertString(ctx, Constants.Multipart.PASSWORD));
                break;
        }

        params.put(Constants.Multipart.TYPE, secretType.toString());

        cpCtx(ctx, params, Constants.Multipart.NAME);
        cpCtx(ctx, params, Constants.Multipart.STORE_PASSWORD);
        cpCtx(ctx, params, Constants.Multipart.GENERATE_PASSWORD);
        cpCtx(ctx, params, Constants.Multipart.VISIBILITY);
        cpCtx(ctx, params, Constants.Multipart.PROJECT_NAME);

        ApiResponse<Map> r = post(ctx, "/api/v1/org/" + org + "/secret", params);
        if (r.getStatusCode() >= 400) {
            throw new ApiException("Failed to CREATE secret. Response: " + r.getData().toString());
        }
        return r.getData();
    }

    private ValidationStatus update(Context ctx) throws ApiException {
        ValidationStatus status = validate(ctx);
        switch (status) {
            case OK:
                if (isEmpty(ctx.getVariable(Constants.Multipart.DATA))) {
                    if (isEmpty(ContextUtils.getString(ctx, NEW_STORE_PASSWORD))) {
                        log.info("Not updating anything since nothing has changed");
                        return ValidationStatus.OK;
                    }
                    String value = get(ctx);
                    delete(ctx);
                    ctx.setVariable(Constants.Multipart.DATA, value);
                    ctx.setVariable(Constants.Multipart.STORE_PASSWORD, ContextUtils.getString(ctx, NEW_STORE_PASSWORD));
                    create(ctx);
                } else {
                    delete(ctx);
                    if (!isEmpty(ContextUtils.getString(ctx, NEW_STORE_PASSWORD))) {
                        ctx.setVariable(Constants.Multipart.STORE_PASSWORD, ContextUtils.getString(ctx, NEW_STORE_PASSWORD));
                    }
                    create(ctx);
                }
                break;
            case MISSING: // If secret is MISSING, we treat it as a CREATE
                create(ctx);
                break;
            case INVALID:
                return ValidationStatus.INVALID;
            case NOT_OWNER:
                return ValidationStatus.NOT_OWNER;
        }
        return ValidationStatus.OK;
    }

    private void delete(Context ctx) throws ApiException {
        if (!ContextUtils.getBoolean(ctx, SKIP_VALIDATION, false)) {
            ValidationStatus status = validate(ctx);
            switch (status) {
                case OK:
                    // Delete the secret
                    break;
                case MISSING:
                    // Already deleted
                    return;
                case INVALID:
                    throw new ApiException("Invalid password for secret");
                case NOT_OWNER:
                    throw new ApiException("Invalid ownership for secret");
            }
        }
        SecretsApi api = new SecretsApi(clientFactory.create(ctx));
        api.delete(assertOrgName(ctx, null), ContextUtils.getString(ctx, Constants.Multipart.NAME));
    }

    private ApiResponse<Map> post(Context ctx, String path, Map<String, Object> params) throws ApiException {
        SecretsApi api = new SecretsApi(clientFactory.create(ctx));
        return ClientUtils.postData(api.getApiClient(), path, params, Map.class);
    }

    private ApiResponse<String> postWithRetry(Context ctx, String path, Map<String, Object> params) throws ApiException {
        SecretsApi api = new SecretsApi(clientFactory.create(ctx));

        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL,
                () -> ClientUtils.postData(api.getApiClient(), path, params, String.class));
    }


    @SuppressWarnings("unchecked")
    private String assertOrgName(Context ctx, String orgName) {
        if (orgName != null) {
            return orgName;
        }

        Map<String, Object> pi = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
        return Optional.ofNullable(pi)
                .map(p -> (String) p.get("orgName"))
                .orElseThrow(() -> new IllegalArgumentException("Organization name not specified"));
    }

    private Action getAction(Context ctx) {
        String action = ContextUtils.getString(ctx, ACTION_KEY, Action.GET.toString());
        try {
            return Action.valueOf(action);
        } catch (Exception e) {
            String message = String.format("Invalid argument '%s', allowed values are: GET(default), VALIDATE, REPLACE, CREATE and DELETE", action);
            throw new IllegalArgumentException(message);
        }
    }

    private SecretEntry.TypeEnum getSecretType(Context ctx) {
        String type = ContextUtils.getString(ctx, Constants.Multipart.TYPE, SecretEntry.TypeEnum.DATA.toString());
        try {
            return SecretEntry.TypeEnum.valueOf(type);
        } catch (Exception e) {
            String message = String.format("Invalid argument '%s', allowed values are: DATA(default), KEY_PAIR and USERNAME_PASSWORD", type);
            throw new IllegalArgumentException(message);
        }
    }

    private void cpCtx(Context ctx, Map<String, Object> parms, String key) {
        if (ctx.getVariable(key) != null) {
            parms.put(key, ctx.getVariable(key));
        }
    }

    private boolean isEmpty(Object o) {
       return o == null || o.equals("") ;
    }

}

