package com.walmartlabs.concord.server.process.pipelines.processors.signing;

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

import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

public class Signing {

    private static final Logger log = LoggerFactory.getLogger(Signing.class);

    private final String keyAlgorithm;
    private final String signingAlgorithm;
    private final PrivateKey privateKey;

    @Inject
    public Signing(ProcessConfiguration cfg) throws Exception {
        this.keyAlgorithm = cfg.getSigningKeyAlgorithm();
        this.signingAlgorithm = cfg.getSigningAlgorithm();

        Path p = cfg.getSigningKeyPath();
        if (p != null) {
            log.info("init -> using {} as the process data signing key", p);
            this.privateKey = loadKey(p);
        } else {
            this.privateKey = null;
        }
    }

    public boolean isEnabled() {
        return this.privateKey != null;
    }

    public String sign(String data) throws Exception {
        byte[] ab = data.getBytes(StandardCharsets.UTF_8);
        byte[] sign = sign(privateKey, ab);
        return Base64.getEncoder().encodeToString(sign);
    }

    private byte[] sign(PrivateKey key, byte[] data) throws GeneralSecurityException {
        Signature s = Signature.getInstance(this.signingAlgorithm);
        s.initSign(key);
        s.update(data);
        return s.sign();
    }

    private PrivateKey loadKey(Path key) throws Exception {
        if (!Files.exists(key)) {
            throw new IllegalArgumentException("Can't load a private key, file not found: " + key);
        }

        byte[] ab = readPemFile(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(ab);
        KeyFactory kf = KeyFactory.getInstance(this.keyAlgorithm);
        return kf.generatePrivate(keySpec);
    }

    private static byte[] readPemFile(Path p) throws IOException {
        String s = Files.readAllLines(p)
                .stream()
                .filter(l -> !l.startsWith("-----"))
                .collect(Collectors.joining());

        return Base64.getDecoder().decode(s);
    }
}
