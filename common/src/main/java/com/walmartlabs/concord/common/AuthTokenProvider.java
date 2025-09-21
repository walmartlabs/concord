package com.walmartlabs.concord.common;

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

import com.walmartlabs.concord.common.cfg.ExternalTokenAuth;
import com.walmartlabs.concord.common.cfg.OauthTokenConfig;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

public interface AuthTokenProvider {

    /**
     * @return {@code true} if this the given repo URI and secret are compatible
     *         with this provider's {@link #getToken(URI, Secret)} method,
     *         {@code false} otherwise.
     */
    boolean supports(URI repo, @Nullable Secret secret);

    Optional<ExternalAuthToken> getToken(URI repo, @Nullable Secret secret);

    default URI addUserInfoToUri(URI repo, @Nullable Secret secret) {
        if (!supports(repo, secret)) {
            // not compatible with auth provider(s)
            return repo;
        }

        return getToken(repo, secret)
                .map(expiringToken -> {
                    var token  = expiringToken.token();
                    var userInfo = expiringToken.username() != null
                            ? expiringToken.username() + ":" + token
                            : token;

                    try {
                        return new URI(repo.getScheme(), userInfo, repo.getHost(),
                                repo.getPort(), repo.getPath(), repo.getQuery(), repo.getFragment());
                    } catch (URISyntaxException e) {
                        // TODO add log?
                    }

                    return null;
                })
                .orElse(repo);
    }

    @SuppressWarnings("ClassCanBeRecord")
    class OauthTokenProvider implements AuthTokenProvider {

        private final List<ExternalTokenAuth> authConfigs;

        @Inject
        public OauthTokenProvider(OauthTokenConfig config) {
            this.authConfigs = toConfigList(config);
        }

        @Override
        public boolean supports(URI repo, @Nullable Secret secret) {
            return validateSecret(secret) || systemSupports(repo);
        }

        @Override
        public Optional<ExternalAuthToken> getToken(URI repo, @Nullable Secret secret) {
            return authConfigs.stream()
                    .filter(auth -> auth.canHandle(repo))
                    .filter(ExternalTokenAuth.Oauth.class::isInstance)
                    .map(ExternalTokenAuth.Oauth.class::cast)
                    .findFirst()
                    .map(auth -> ExternalAuthToken.StaticToken.builder()
                            .token(auth.token())
                            .username(auth.username().orElse(null))
                            .build());
        }

        private boolean validateSecret(Secret secret) {
            if (secret == null) {
                return false;
            }

            if (!(secret instanceof BinaryDataSecret bds)) {
                // this class is not the place for handling key pairs or username/password
                return false;
            } else {
                String data = new String(bds.getData());
                // >0 length, printable ascii (no newlines, etc)
                return data.matches("[ -~]+");
            }
        }

        private boolean systemSupports(URI repoUri) {
            return authConfigs.stream().anyMatch(auth -> auth.canHandle(repoUri));
        }

        private static List<ExternalTokenAuth> toConfigList(OauthTokenConfig config) {
            var token = config.getOauthToken().orElse(null);

            if (token == null || token.isBlank() && config.getSystemAuth().isEmpty()) {
                return config.getSystemAuth();
            }

            return List.of(ExternalTokenAuth.Oauth.builder()
                    .token(token)
                    .username(config.getOauthUsername())
                    .urlPattern(ExternalTokenAuth.assertBaseUrlPattern(config.getOauthUrlPattern().orElse(".*")))// for backwards compat with git.oauth
                    .build());
        }
    }
}
