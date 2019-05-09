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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation")
public class DeprecatedArgsProcessor {

    private static final Logger log = LoggerFactory.getLogger(DeprecatedArgsProcessor.class);

    public static Map<String, Object> process(Path workDir, Map<String, Object> args) {
        Map<String, Object> result = new HashMap<>(args);

        processPrivateKey(workDir, result);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static void processPrivateKey(Path workDir, Map<String, Object> args) {
        Object privateKey = args.get(TaskParams.PRIVATE_KEY_FILE_KEY.getKey());
        if (privateKey != null) {
            log.warn("'{}' is deprecated, please use '{}.{}' parameter", TaskParams.PRIVATE_KEY_FILE_KEY.getKey(), TaskParams.AUTH.getKey(), "privateKey");
        }

        Object user = args.get(TaskParams.USER_KEY.getKey());
        if (user != null) {
            log.warn("'{}' is deprecated, please use '{}.{}' parameter", TaskParams.USER_KEY.getKey(), TaskParams.AUTH.getKey(), "user");
        }

        Map<String, Object> privateKeyParams = new HashMap<>();
        if (privateKey instanceof Map) {

            Map<String, Object> m = (Map<String, Object>) privateKey;
            String name = (String) m.get("secretName");
            String password = (String) m.get("password");
            String orgName = (String) m.get("org");

            Map<String, Object> secretParams = new HashMap<>();
            secretParams.put("org", orgName);
            secretParams.put("name", name);
            secretParams.put("password", password);

            privateKeyParams.put("secret", secretParams);
            privateKeyParams.put("user", user);
        } else {
            String path = (String) privateKey;
            if (path == null) {
                path = TaskParams.PRIVATE_KEY_FILE_NAME.getKey();
                if (!Files.exists(workDir.resolve(path))) {
                    path = null;
                } else {
                    log.warn("'{}' is deprecated, please use '{}.{}' parameter", TaskParams.PRIVATE_KEY_FILE_NAME.getKey(), TaskParams.AUTH.getKey(), "privateKey");
                }
            }
            if (path != null) {
                privateKeyParams.put("path", path);
                privateKeyParams.put("user", user);
            }
        }

        if (!privateKeyParams.isEmpty()) {
            args.put(TaskParams.AUTH.getKey(), Collections.singletonMap("privateKey", privateKeyParams));
        }
    }
}
