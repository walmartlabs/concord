package com.walmartlabs.concord.server.org.policy;

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

import com.walmartlabs.concord.server.AbstractDaoTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.UUID;

@Ignore("requires a local DB instance")
public class PolicyDaoTest extends AbstractDaoTest {

    private PolicyDao policyDao;

    @Before
    public void setUp() {
        policyDao = new PolicyDao(getConfiguration());
    }

    @Test
    public void test() {
        UUID orgId = null;
        UUID projectId = null;
        UUID userId = null;

        PolicyRules result = policyDao.getRules(orgId, projectId, userId);
        System.out.println(">>>" + result);
    }
}
