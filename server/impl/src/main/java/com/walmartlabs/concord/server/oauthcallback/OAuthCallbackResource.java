package com.walmartlabs.concord.server.oauthcallback;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.events.ExpressionUtils;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.sdk.rest.Resource;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Path("/api/v1/oauth")
@Tag(name = "OAuth")
@Named
public class OAuthCallbackResource implements Resource {

    private static final Logger log = LoggerFactory.getLogger(OAuthCallbackResource.class);


    private final SecretStoreConfiguration secretCfg;
    private final ProcessManager processManager;
    private final PayloadManager payloadManager;
    private final ProcessKeyCache processKeyCache;


    @Inject
    public OAuthCallbackResource(SecretStoreConfiguration secretCfg, ProcessManager processManager, PayloadManager payloadManager, ProcessKeyCache processKeyCache) {
        this.secretCfg = secretCfg;
        this.processManager = processManager;
        this.payloadManager = payloadManager;
        this.processKeyCache = processKeyCache;
    }

    @GET
    @Path("/callback")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    @Operation(description = "Callback endpoint for oauth", operationId = "oAuthCallback")
    public Response handleCallback(@NotNull @QueryParam("code") String code, @NotNull @QueryParam("state") String stateEncoded, @Context UriInfo uriInfo) {
        String state = new String(Base64.getDecoder().decode(stateEncoded.getBytes()));

        UUID instanceId = UUID.fromString(state.split("\\|")[0]);
        String eventName = state.split("\\|")[1];
        String sessionToken = state.split("\\|")[2];

        UUID instanceIdFromSessionToken = decryptSessionKey(sessionToken);
        if (!instanceIdFromSessionToken.equals(instanceId)) {
            throw new UnauthorizedException("Invalid session token");
        }

        Map<String, Object> arguments = Map.of("oAuthAuthorizationCode", code);
        Map<String, Object> req = Map.of("arguments", arguments);

        ProcessKey processKey = processKeyCache.get(instanceId);
        if (processKey == null) {
            throw new ConcordApplicationException("Process instance not found: " + instanceId,
                    Response.Status.NOT_FOUND);
        }

        processManager.assertResumeEvents(processKey, Set.of(eventName));

        req = ExpressionUtils.escapeMap(req);

        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            log.error("resume ['{}', '{}'] -> error creating a payload: {}", instanceId, eventName, e.getMessage());
            throw new ConcordApplicationException("Error creating a payload", e);
        }

        processManager.resume(payload);

        return Response.temporaryRedirect(uriInfo.getBaseUri().resolve("#/process/" + instanceId + "/log")).build();

    }

    private UUID decryptSessionKey(String h) {
        var salt = secretCfg.getSecretStoreSalt();
        var pwd = secretCfg.getServerPwd();
        var ab = SecretUtils.decrypt(Base64.getDecoder().decode(h), pwd, salt);
        return UUID.fromString(new String(ab));
    }

}
