package com.walmartlabs.concord.server.security.apikey;

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
import org.junit.Ignore;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.UUID;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class ApiKeyDaoTest extends AbstractDaoTest {

    @Test
    public void testDefaultAdminToken() throws Exception {
        ApiKeyDao m = new ApiKeyDao(getConfiguration(), mock(SecureRandom.class));
        UUID id = m.findUserId("auBy4eDWrKWsyhiDp3AQiw");
        assertNotNull(id);
    }
}
