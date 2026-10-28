package com.walmartlabs.concord.server.org.inventory;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
@Deprecated
public class InventoryOwner implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final ExternalIdentityKey identity;
    private final String username;
    private final String profileProviderId;
    private final Set<String> identityProviderIds;

    public InventoryOwner(UUID id, String username) {
        this(id, username, null, null, null);
    }

    @JsonCreator
    public InventoryOwner(@JsonProperty("id") UUID id,
                          @JsonProperty("username") String username,
                          @JsonProperty("identity") ExternalIdentityKey identity,
                          @JsonProperty("profileProviderId") String profileProviderId,
                          @JsonProperty("identityProviderIds") Set<String> identityProviderIds) {

        this.id = id;
        this.identity = identity;
        this.username = username;
        this.profileProviderId = profileProviderId;
        this.identityProviderIds = identityProviderIds;
    }

    public UUID getId() {
        return id;
    }

    public ExternalIdentityKey getIdentity() {
        return identity;
    }

    public String getUsername() {
        return username;
    }

    public String getProfileProviderId() {
        return profileProviderId;
    }

    public Set<String> getIdentityProviderIds() {
        return identityProviderIds;
    }

    @Override
    public String toString() {
        return "InventoryOwner{" +
                "id=" + id +
                ", username='" + username + '\'' +
                '}';
    }
}
