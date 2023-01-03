package com.walmartlabs.concord.server.org.secret;

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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.Authorization;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Named
@Singleton
@Api(value = "Secret stores", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/secret/store")
public class SecretStoreResource implements Resource {

    private final SecretManager secretManager;

    @Inject
    public SecretStoreResource(SecretManager secretManager) {
        this.secretManager = secretManager;
    }

    @GET
    @ApiOperation(value = "List of active secret stores", responseContainer = "list", response = SecretStoreEntry.class)
    @Produces(MediaType.APPLICATION_JSON)
    public List<SecretStoreEntry> listActiveStores() {
        return secretManager.getActiveSecretStores().stream()
                .map(s -> new SecretStoreEntry(s.getType(), s.getDescription()))
                .collect(Collectors.toList());
    }
}
