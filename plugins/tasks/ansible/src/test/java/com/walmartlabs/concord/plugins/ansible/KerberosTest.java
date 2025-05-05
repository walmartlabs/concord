package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.SystemTimeProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Disabled
public class KerberosTest {

    @Test
    public void a() throws Exception {
        Path tmpPath = Files.createTempDirectory("krb-test");
        System.out.println(">>" + tmpPath);

        String username = "USER";
        String password = "PASSWORD";
        KerberosAuth kerberos = new KerberosAuth(new SystemTimeProvider(), username, password, tmpPath, false);

        kerberos.prepare();

        Thread.sleep(TimeUnit.MINUTES.toMillis(5));

        kerberos.postProcess();
    }
}
