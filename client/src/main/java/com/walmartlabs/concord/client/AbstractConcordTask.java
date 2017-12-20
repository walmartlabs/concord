package com.walmartlabs.concord.client;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.sdk.ApiConfiguration;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;

import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.client.Keys.SESSION_TOKEN_KEY;

public abstract class AbstractConcordTask implements Task {

    @Inject
    ApiConfiguration apiCfg;

    public AbstractConcordTask() {
        this.apiCfg = null;
    }

    protected ResteasyClient createClient() {
        return new ResteasyClientBuilder()
                .establishConnectionTimeout(30, TimeUnit.SECONDS)
                .socketTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    protected <T> T withClient(Context ctx, CheckedFunction<ResteasyWebTarget, T> f) throws Exception {
        return withClient(ctx, null, f);
    }

    protected <T> T withClient(Context ctx, String uri, CheckedFunction<ResteasyWebTarget, T> f) throws Exception {
        ResteasyClient client = createClient();
        client.register((ClientRequestFilter) requestContext -> {
            MultivaluedMap<String, Object> headers = requestContext.getHeaders();
            headers.putSingle("X-Concord-SessionToken", apiCfg.getSessionToken(ctx));
        });

        String targetUri = apiCfg.getBaseUrl();
        if (uri != null) {
            targetUri += "/" + uri;
        }

        ResteasyWebTarget target = client.target(targetUri);
        try {
            return f.apply(target);
        } finally {
            client.close();
        }

    }

    protected <T> T request(Context ctx, String uri, Map<String, Object> input, Class<T> entityType) throws Exception {
        return withClient(ctx, uri, target -> {
            MultipartFormDataOutput mdo = createMDO(input);
            GenericEntity<MultipartFormDataOutput> entity = new GenericEntity<MultipartFormDataOutput>(mdo) {
            };

            Response resp = target.request().post(Entity.entity(entity, MediaType.MULTIPART_FORM_DATA));
            if (resp.getStatus() != Response.Status.OK.getStatusCode()) {
                resp.close();
                if (resp.getStatus() == 403) {
                    throw new ForbiddenException();
                }

                throw new WebApplicationException(resp);
            }

            T e = resp.readEntity(entityType);
            resp.close();
            return e;
        });
    }

    protected MultipartFormDataOutput createMDO(Map<String, Object> input) {
        MultipartFormDataOutput mdo = new MultipartFormDataOutput();
        input.forEach((k, v) -> {
            if (v instanceof InputStream || v instanceof byte[]) {
                mdo.addFormData(k, v, MediaType.APPLICATION_OCTET_STREAM_TYPE);
            } else if (v instanceof String) {
                mdo.addFormData(k, v, MediaType.TEXT_PLAIN_TYPE);
            } else if (v instanceof Map) {
                mdo.addFormData(k, v, MediaType.APPLICATION_JSON_TYPE);
            } else if (v instanceof Boolean) {
                mdo.addFormData(k, v.toString(), MediaType.TEXT_PLAIN_TYPE);
            } else {
                throw new IllegalArgumentException("Unknown input type: " + v);
            }
        });
        return mdo;
    }

    protected Map<String, Object> createCfg(Context ctx) {
        return createCfg(ctx, (String[]) null);
    }

    protected Map<String, Object> createCfg(Context ctx, String ... keys) {
        Map<String, Object> m = new HashMap<>();

        String sessionToken = apiCfg.getSessionToken(ctx);
        if (sessionToken != null) {
            m.put(SESSION_TOKEN_KEY, sessionToken);
        }

        for (String k : keys) {
            Object v = ctx.getVariable(k);
            if (v != null) {
                m.put(k, v);
            }
        }

        return m;
    }

    @SuppressWarnings("unchecked")
    protected static <T> T get(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) {
            throw new IllegalArgumentException("'" + k + "' is required");
        }
        return (T) v;
    }

    @FunctionalInterface
    protected interface CheckedFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
