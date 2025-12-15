package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.SecretUtils;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

public class GitAuthResolver {

    public static String resolveOAuthToken(String repoUrl, GitClientConfiguration cfg) {
        List<GitAuthProvider> providers = cfg.systemGitAuthProviders();
        if (providers == null) {
            return null;
        }

        for (GitAuthProvider provider : providers) {
            String baseUrl = provider.baseUrl();
            if (baseUrl == null) {
                continue;
            }
            if (!repoUrl.startsWith(baseUrl)) {
                continue;
            }

            if (provider.authType() == AuthType.OAUTH_TOKEN) {
                return provider.oauthToken();
            } else if (provider.authType() == AuthType.GITHUB_APP_INSTALLATION) {
                String installationId = provider.installationId();
                String clientId = provider.clientId();
                String privateKeyPem = provider.privateKey();
                if (installationId == null || clientId == null || privateKeyPem == null) {
                    continue; // incomplete config, try next
                }
                try {
                    PrivateKey privateKey = toPrivateKey(privateKeyPem);
                    // Order: installationId, clientId, privateKey (per request)
                    return SecretUtils.generateGitHubInstallationToken(provider.clientId(), privateKey, installationId);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to generate GitHub installation token", e);
                }
            }
        }
        return null;
    }

    private static PrivateKey toPrivateKey(String pem) throws Exception {
        String normalized = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(normalized);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
}
