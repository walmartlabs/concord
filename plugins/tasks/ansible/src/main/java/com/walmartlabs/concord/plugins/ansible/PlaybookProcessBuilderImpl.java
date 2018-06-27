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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PrivilegedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * @deprecated prefer {@link DockerPlaybookProcessBuilder}
 */
@Deprecated
public class PlaybookProcessBuilderImpl implements PlaybookProcessBuilder {

    private static final Logger log = LoggerFactory.getLogger(PlaybookProcessBuilderImpl.class);

    private final String workDir;

    private boolean debug;

    public PlaybookProcessBuilderImpl(String workDir) {
        this.workDir = workDir;
    }

    public PlaybookProcessBuilderImpl withDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    @Override
    public Process build(List<String> args, Map<String, String> extraEnv) throws IOException {
        File pwd = new File(workDir);
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

        ProcessBuilder b = new ProcessBuilder()
                .command(cmd)
                .directory(pwd)
                .redirectErrorStream(true);

        Map<String, String> env = b.environment();
        env.putAll(extraEnv);

        if (debug) {
            log.info("build -> env: {}", env);
        }

        return PrivilegedAction.perform("task", b::start);
    }
}
