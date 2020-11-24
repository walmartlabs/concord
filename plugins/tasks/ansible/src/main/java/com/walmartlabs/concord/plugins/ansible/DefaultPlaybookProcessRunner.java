package com.walmartlabs.concord.plugins.ansible;

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

import com.walmartlabs.concord.common.PrivilegedAction;
import com.walmartlabs.concord.common.TruncBufferedReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DefaultPlaybookProcessRunner implements PlaybookProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlaybookProcessRunner.class);
    private static final Logger processLog = LoggerFactory.getLogger("processLog");

    private final Path workDir;

    private boolean debug;

    public DefaultPlaybookProcessRunner(Path workDir) {
        this.workDir = workDir;
    }

    public DefaultPlaybookProcessRunner withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    @Override
    public int run(List<String> args, Map<String, String> extraEnv) throws IOException, InterruptedException {
        File pwd = workDir.toFile();
        if (!pwd.exists()) {
            throw new IOException("Working directory not found: " + pwd);
        }

        if (debug) {
            log.info("build -> working directory: {}", pwd);
        }

        String[] cmd = args.toArray(new String[0]);

        if (debug) {
            log.info("build -> cmd: {}", String.join(" ", cmd));
        }

        java.lang.ProcessBuilder b = new java.lang.ProcessBuilder()
                .command(cmd)
                .directory(pwd)
                .redirectErrorStream(true);

        Map<String, String> env = b.environment();
        env.putAll(extraEnv);

        if (debug) {
            log.info("build -> env: {}", env);
        }

        Process p = PrivilegedAction.perform("task", b::start);

        BufferedReader reader = new TruncBufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            processLog.info("ANSIBLE: {}", line);
        }

        return p.waitFor();
    }
}
