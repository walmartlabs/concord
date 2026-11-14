package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiKeysApi;
import com.walmartlabs.concord.client2.CreateApiKeyRequest;
import com.walmartlabs.concord.client2.CreateUserRequest;
import com.walmartlabs.concord.client2.RoleEntry;
import com.walmartlabs.concord.client2.RolesApi;
import com.walmartlabs.concord.client2.UsersApi;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsIT extends AbstractServerIT {

    @Test
    void scrapeRequiresReadMetricsPermission() throws Exception {
        var name = "metrics_" + UUID.randomUUID().toString().replace("-", "");
        var roles = new RolesApi(getApiClient());
        var users = new UsersApi(getApiClient());
        roles.createOrUpdateRole(new RoleEntry().name(name).permissions(Set.of()));
        var user = users.createOrUpdateUser(new CreateUserRequest()
                .username(name).type(CreateUserRequest.TypeEnum.LOCAL).roles(Set.of(name)));
        try (var client = HttpClient.newHttpClient()) {
            var key = new ApiKeysApi(getApiClient()).createUserApiKey(new CreateApiKeyRequest().username(name)).getKey();
            var request = HttpRequest.newBuilder(URI.create(ITConstants.SERVER_URL + "/metrics"))
                    .header("Authorization", "Bearer " + key)
                    .build();
            assertEquals(403, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
            roles.createOrUpdateRole(new RoleEntry().name(name).permissions(Set.of("readMetrics")));
            assertEquals(200, client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode());
        } finally {
            users.deleteUser(user.getId());
            roles.deleteRole(name);
        }
    }
}
