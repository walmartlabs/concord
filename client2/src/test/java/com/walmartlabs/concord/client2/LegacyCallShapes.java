package com.walmartlabs.concord.client2;

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

import java.util.List;
import java.util.UUID;

/** Source-compatibility fixture: compiled with the generated client, not executed. */
final class LegacyCallShapes {
    static void use(ApiClient client, UUID userId) throws ApiException {
        CreateApiKeyRequest key = new CreateApiKeyRequest()
                .userId(userId).username("user").userDomain("domain")
                .userType(CreateApiKeyRequest.UserTypeEnum.LDAP).name("key").key("value");
        ApiKeysApi keys = new ApiKeysApi(client);
        keys.createUserApiKey(key);
        keys.listUserApiKeys(userId);
        new ApiKeysV2Api(client).createOrUpdateUserApiKey(key);

        UsersApi users = new UsersApi(client);
        users.createOrUpdateUser(new CreateUserRequest().username("user").userDomain("domain")
                .type(CreateUserRequest.TypeEnum.LOCAL));
        users.findByUsername("user");
        users.updateUserRoles("user", new UpdateUserRolesRequest());

        TeamUserEntry member = new TeamUserEntry().username("user").userDomain("domain")
                .userType(TeamUserEntry.UserTypeEnum.LDAP).role(TeamUserEntry.RoleEnum.MEMBER)
                .memberType(TeamUserEntry.MemberTypeEnum.LDAP_GROUP).ldapGroupSource("group");
        TeamsApi teams = new TeamsApi(client);
        teams.addUsersToTeam("org", "team", false, List.of(member));
        teams.removeUsersFromTeam("org", "team", List.of("user"));

        PolicyApi policies = new PolicyApi(client);
        policies.linkPolicy("policy", new PolicyLinkEntry().userName("user").userDomain("domain"));
        policies.listPolicies("org", "project", "user", "domain", "LDAP");
        policies.unlinkPolicy("policy", "org", "project", "user", "domain", "LDAP");
    }
}
