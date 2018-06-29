package com.walmartlabs.concord.it.runner;

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

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.Assert.assertEquals;

public class CallingTasksIT extends AbstractRunnerIT {

    @Test
    public void test() throws Exception {
        // prepare the payload

        Path workDir = Files.createTempDirectory("test");
        IOUtils.copy(Paths.get(CallingTasksIT.class.getResource("callingTasks").toURI()), workDir);

        // add dependencies

        Path depsDir = Paths.get(System.getenv("IT_DEPS_DIR"));
        IOUtils.copy(depsDir, workDir.resolve(Constants.Files.LIBRARIES_DIR_NAME));

        // start the process

        String instanceId = UUID.randomUUID().toString();
        Process proc = exec(instanceId, workDir);

        // check the logs

        byte[] ab = readLog(proc);
        System.out.println(new String(ab));

        int code = proc.waitFor();
        assertEquals(0, code);

        assertEquals(1, grep(".*ANSIBLE.*Hello!.*", ab).size());
    }
}
