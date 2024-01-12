package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.server.sdk.rest.Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/secret/store")
@Tag(name = "Secret stores")
public class SecretStoreResource implements Resource {

    private final SecretManager secretManager;

    @Inject
    public SecretStoreResource(SecretManager secretManager) {
        this.secretManager = secretManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List of active secret stores")
    public List<SecretStoreEntry> listActiveStores() {
        return secretManager.getActiveSecretStores().stream()
                .map(s -> new SecretStoreEntry(s.getType(), s.getDescription()))
                .collect(Collectors.toList());
    }
}
