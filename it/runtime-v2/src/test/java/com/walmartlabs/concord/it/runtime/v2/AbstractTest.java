package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.it.common.JGitUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Timeout;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public abstract class AbstractTest {

    @BeforeAll
    public static void init() {
        JGitUtils.applyWorkarounds();
    }

    protected static ProcessEntry expectStatus(ConcordProcess proc, ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum... more) throws ApiException {
        try {
            return proc.expectStatus(status, more);
        } catch (Exception e) {
            System.out.println("Process log:");
            System.out.println(new String(proc.getLog()));
            throw e;
        }
    }

    protected URI resource(String name) throws URISyntaxException {
        URL url = getClass().getResource(name);
        assertNotNull(url, "can't find '" + name + "'");
        return url.toURI();
    }
}
