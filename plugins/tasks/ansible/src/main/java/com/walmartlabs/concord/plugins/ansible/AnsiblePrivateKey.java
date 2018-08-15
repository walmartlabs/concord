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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.ContextUtils;
import com.walmartlabs.concord.sdk.SecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AnsiblePrivateKey {

    private static final Logger log = LoggerFactory.getLogger(AnsiblePrivateKey.class);

    private final SecretService secretService;
    private final Context context;
    private final Path workDir;

    private Path keyPath;

    public AnsiblePrivateKey(SecretService secretService, Context context, Path workDir) {
        this.secretService = secretService;
        this.context = context;
        this.workDir = workDir;
    }

    public static Path process(SecretService secretService, Context context, Path workDir, Map<String,Object> args) {
        return new AnsiblePrivateKey(secretService, context, workDir).parse(args).getKeyPath();
    }

    public AnsiblePrivateKey parse(Map<String, Object> args) {
        try {
            keyPath = getPrivateKeyPath(args);
        } catch (Exception e) {
            log.error("Error while fetching the private key: {}", e.getMessage(), e);
            throw new RuntimeException("Error while fetching the private key: " + e.getMessage());
        }

        return this;
    }

    public Path getKeyPath() {
        if (keyPath == null) {
            return null;
        }
        return workDir.relativize(keyPath);
    }

    @SuppressWarnings("unchecked")
    private Path getPrivateKeyPath(Map<String, Object> args) throws Exception {
        Path p;
        Object o = args.get(TaskParams.PRIVATE_KEY_FILE_KEY.getKey());
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            String name = (String) m.get("secretName");
            if (name == null) {
                throw new IllegalArgumentException("Secret name is required to export a private key");
            }

            String password = (String) m.get("password");
            String orgName = (String) m.get("org");
            String txId = ContextUtils.assertString(context, Constants.Context.TX_ID_KEY);
            Map<String, String> keyPair = secretService.exportKeyAsFile(context, txId, workDir.toAbsolutePath().toString(), orgName, name, password);
            p = Paths.get(keyPair.get("private"));
        } else {
            p = ArgUtils.getPath(args, TaskParams.PRIVATE_KEY_FILE_KEY.getKey(), workDir);
        }

        if (p == null) {
            p = workDir.resolve(TaskParams.PRIVATE_KEY_FILE_NAME.getKey());
            if (!Files.exists(p)) {
                return null;
            }
        }

        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Private key file not found: " + p);
        }

        log.info("Using the private key: {}", p);

        // ensure that the key has proper permissions (chmod 600)
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);

        return p.toAbsolutePath();
    }
}
