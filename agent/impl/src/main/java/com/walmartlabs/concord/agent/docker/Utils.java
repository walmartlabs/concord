package com.walmartlabs.concord.agent.docker;

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


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public final class Utils {

    public static void exec(String[] cmd, Callback callback) throws IOException, InterruptedException {
        Process b = new ProcessBuilder()
                .command(cmd)
                .redirectErrorStream(true)
                .start();

        int code = b.waitFor();
        if (code != 0) {
            throw new IOException("Error while executing a command " + String.join(" ", cmd) + " : docker exit code " + code);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(b.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                callback.call(line);
            }
        }
    }

    public interface Callback {

        void call(String line);
    }

    private Utils() {
    }
}
