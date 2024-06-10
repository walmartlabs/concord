package com.walmartlabs.concord.it.server;

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
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserResourceIT extends AbstractServerIT {

    @Test
    public void testUserList() throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // add the user A

        String userAName = "testUserList_userA_" + randomString();
        addUser(userAName);

        // add the user B

        String userBName = "testUserList_userB_" + randomString();
        addUser(userBName);

        // list users

        UserV2Api userApi = new UserV2Api(getApiClient());
        List<UserEntry> result = userApi.listUsersWithFilter(0, 2, "testUserList_");
        assertEquals(2, result.size());
        result.sort(Comparator.comparing(UserEntry::getName));
        assertEquals(userAName, result.get(0).getName());
        assertEquals(userBName, result.get(1).getName());
    }

    private void addUser(String userAName) throws ApiException {
        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest().username(userAName)
                .type(CreateUserRequest.TypeEnum.LOCAL));
    }
}
