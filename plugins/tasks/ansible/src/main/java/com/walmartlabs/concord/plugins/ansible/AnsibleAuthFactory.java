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

import com.walmartlabs.concord.runtime.v2.sdk.SecretService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;

import static com.walmartlabs.concord.sdk.MapUtils.*;

public class AnsibleAuthFactory {

    private static final Logger log = LoggerFactory.getLogger(AnsibleAuthFactory.class);

    private final AnsibleSecretService secretService;

    public AnsibleAuthFactory(AnsibleSecretService secretService) {
        this.secretService = secretService;
    }

    public AnsibleAuth create(AnsibleContext context) {
        Map<String, Map<String, Object>> authParams = getMap(context.args(), TaskParams.AUTH, Collections.emptyMap());
        if (authParams.isEmpty()) {
            return new NopAuth();
        }

        if (authParams.size() != 1) {
            throw new RuntimeException("Invalid auth configuration. More that one auth type (expected one of 'krb5' or 'privateKey'): " + authParams.keySet());
        }

        Map.Entry<String, Map<String, Object>> auth = authParams.entrySet().iterator().next();
        switch (auth.getKey().toLowerCase()) {
            case "krb5":
                try {
                    SecretService.UsernamePassword cred = parseKerberosAuth(secretService, auth.getValue());
                    log.info("Using the kerberos username: {}", cred.username());
                    return new KerberosAuth(cred.username(), cred.password(), context.tmpDir(), context.debug());
                } catch (Exception e) {
                    log.error("Error while fetching the kerberos credentials: {}", e.getMessage(), e);
                    throw new RuntimeException("Error while fetching the kerberos credentials: " + e.getMessage());
                }
            case "privatekey":
                try {
                    Map<String, Object> cred = parsePrivateKeyAuth(secretService, context.workDir(), auth.getValue());
                    String username = getString(cred, "user");
                    Path privateKeyPath = assertPath(cred, "keyPath");
                    log.info("Using the private key: {}", privateKeyPath);
                    return new PrivateKeyAuth(context.workDir(), username, privateKeyPath);
                } catch (Exception e) {
                    log.error("Error while fetching the private key: {}", e.getMessage(), e);
                    throw new RuntimeException("Error while fetching the private key: " + e.getMessage());
                }
            default:
                throw new IllegalArgumentException("Unknown auth type: " + auth);
        }
    }

    private static SecretService.UsernamePassword parseKerberosAuth(AnsibleSecretService secretService,
                                                                    Map<String, Object> auth) throws Exception {
        Map<String, Object> secretParams = getMap(auth, "secret", Collections.emptyMap());
        if (!secretParams.isEmpty()) {
            Secret secret = Secret.from(secretParams);
            return secretService.exportCredentials(secret.getOrg(), secret.getName(), secret.getPassword());
        }

        return SecretService.UsernamePassword.of(assertString(auth, "user"), assertString(auth, "password"));
    }

    private static Map<String, Object> parsePrivateKeyAuth(AnsibleSecretService secretService,
                                                           Path workDir,
                                                           Map<String, Object> auth) throws Exception {

        Path p;
        Map<String, Object> secretParams = getMap(auth, "secret", Collections.emptyMap());
        if (!secretParams.isEmpty()) {
            Secret secret = Secret.from(secretParams);
            SecretService.KeyPair keyPair = secretService.exportKeyAsFile(secret.getOrg(), secret.getName(), secret.getPassword());
            p = keyPair.privateKey();
        } else {
            p = ArgUtils.getPath(auth, "path", workDir);
        }

        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Private key file not found: " + p);
        }

        // ensure that the key has proper permissions (chmod 600)
        Set<PosixFilePermission> perms = new HashSet<>();
        perms.add(PosixFilePermission.OWNER_READ);
        perms.add(PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(p, perms);

        Map<String, Object> result = new HashMap<>();
        result.put("user", getString(auth, "user"));
        result.put("keyPath", p.toAbsolutePath());
        return result;
    }

    private static Path assertPath(Map<String, Object> m, String key) {
        Object p = m.get(key);
        if (p == null) {
            throw new IllegalArgumentException("Mandatory variable '" + key + "' is required");
        }
        if (p instanceof Path) {
            return (Path) p;
        }
        throw new IllegalArgumentException("Invalid variable '" + key + "' type, expected: path, got: " + p.getClass());
    }
}
