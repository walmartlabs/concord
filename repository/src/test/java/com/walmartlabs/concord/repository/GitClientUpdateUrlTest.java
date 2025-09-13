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
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class GitClientUpdateUrlTest {

    @Test
    public void testTwoSystemProvidersBothAuthTypes() throws Exception {
        GitAuthProvider oauth = Mockito.mock(GitAuthProvider.class);
        when(oauth.baseUrl()).thenReturn("https://example.com");
        when(oauth.authType()).thenReturn(AuthType.OAUTH_TOKEN);
        when(oauth.oauthToken()).thenReturn("oauthProvTok");

        GitAuthProvider gha = Mockito.mock(GitAuthProvider.class);
        when(gha.baseUrl()).thenReturn("https://gh.example.com");
        when(gha.authType()).thenReturn(AuthType.GITHUB_APP_INSTALLATION);
        when(gha.installationId()).thenReturn("12345");
        when(gha.clientId()).thenReturn("clientId123");
        when(gha.privateKey()).thenReturn(dummyPrivateKeyPem());

        GitClientConfiguration cfg = Mockito.mock(GitClientConfiguration.class);
        when(cfg.oauthToken()).thenReturn(null); // no global token
        when(cfg.authorizedGitHosts()).thenReturn(List.of("example.com", "gh.example.com"));
        when(cfg.systemGitAuthProviders()).thenReturn(List.of(oauth, gha));
        mockTiming(cfg);

        try (MockedStatic<SecretUtils> ms = Mockito.mockStatic(SecretUtils.class)) {
            ms.when(() -> SecretUtils.generateGitHubInstallationToken(eq("clientId123"), any(), eq("12345")))
                    .thenReturn("appTok");
            GitClient client = new GitClient(cfg);

            String r1 = invokeUpdateUrl(client, "https://example.com/repo.git", null);
            assertEquals("https://oauthProvTok@example.com/repo.git", r1);

            String r2 = invokeUpdateUrl(client, "https://gh.example.com/owner/repo.git", null);
            assertEquals("https://appTok@gh.example.com/owner/repo.git", r2);
        }
    }

    @Test
    public void testSingleGitHubAppProvider() throws Exception {
        GitClientConfiguration cfg = Mockito.mock(GitClientConfiguration.class);
        when(cfg.oauthToken()).thenReturn("defaultGitAuth"); // no global token
        when(cfg.authorizedGitHosts()).thenReturn(List.of("example.com", "gh.example.com"));
        when(cfg.systemGitAuthProviders()).thenReturn(null);
        mockTiming(cfg);

        try (MockedStatic<SecretUtils> ms = Mockito.mockStatic(SecretUtils.class)) {
            ms.when(() -> SecretUtils.generateGitHubInstallationToken(eq("clientId123"), any(), eq("12345")))
                    .thenReturn("appTok");
            GitClient client = new GitClient(cfg);

            // default url should be returned as-is
            String r1 = invokeUpdateUrl(client, "https://example.com/repo.git", null);
            assertEquals("https://defaultGitAuth@example.com/repo.git", r1);
        }
    }

    private static void mockTiming(GitClientConfiguration cfg) {
        when(cfg.fetchTimeout()).thenReturn(Duration.ofSeconds(30));
        when(cfg.defaultOperationTimeout()).thenReturn(Duration.ofSeconds(30));
        when(cfg.httpLowSpeedLimit()).thenReturn(0);
        when(cfg.httpLowSpeedTime()).thenReturn(Duration.ofSeconds(0));
        when(cfg.sshTimeout()).thenReturn(Duration.ofSeconds(30));
        when(cfg.sshTimeoutRetryCount()).thenReturn(1);
    }

    private static String invokeUpdateUrl(GitClient client, String url, com.walmartlabs.concord.sdk.Secret secret) throws Exception {
        Method m = GitClient.class.getDeclaredMethod("updateUrl", String.class, com.walmartlabs.concord.sdk.Secret.class);
        m.setAccessible(true);
        return (String) m.invoke(client, url, secret);
    }

    private static String dummyPrivateKeyPem() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDObzvB6PvW6QEz\n"
                + "fVkR6Tn6kG6DgGQ2wxYMX+0X2PkaIu+lX2ftheszl1St7uw0lP0k6j9jDF6v74mt\n"
                + "mCjJ3Y1KzXoNTcCaqmReyFm2dwAWyMNhg5FrCce8K9ID1NDuKCYN6VRPvz9lcJ4Q\n"
                + "lQ01vPSg6gLhGv+RtHnF9R9RC1K0M6EKi0mD1kZPRBuZJr4O7YCO00zXGbWJ3QMi\n"
                + "7R3p5ghPT23HB8s1dBZU9fairyGbQHcBjGAslv+YfyKxo6LnV+JZK8qnDwP0oDjg\n"
                + "GgL3wfr8PqXQNe7y6ojVYhXG1ob6gwpPb4gZ/QgkXVk9M879b8Sly/GbN6bIM1KX\n"
                + "PPcL7Dc3AgMBAAECggEAGy6zhi1f2cW7lCGS3BSB1iZpAnLT2xx9UpRZVc4hPPES\n"
                + "CtaQoJDX9i8Aa/vuHXe3UioYTFnTt98BFzq7J7ZP5m4O79lyULqZKXdyC1VVn21P\n"
                + "AN2eRwyMi9CuavHwy3WvZbQXz3QNrmYJqZBN1uJGiH8K42Z6mV4Ut1irl65fPHXC\n"
                + "o9gpq5JaGoMfjd+ZfA3c/Rr9p2MqV1WsTb96bWYxHkEAr7InB/vBY8dMus5msmqs\n"
                + "1b5UmJce2W11MDUzmv23whumFcuVQH3Sbeocv1suZ/f1mE2roGwexkR6A4olh+3c\n"
                + "0JY3X08rNOQ9SWulzAsDUd3o9sZIlTmvb2EvfRtciQKBgQDqS0X5qL0PTK5ZfphQ\n"
                + "xHATXSZqOig6GaxUc6kB0iOMlIkdT6DAxF/Efj6cv9h/k0GwZC3XVUZBBgMscc4c\n"
                + "IfpqADSxtARHbN2Ac0PlhwX8mXawrw/yzbH6M64yDkPOblRKNp4ZgK5bPwkSc3ED\n"
                + "0Ep0Nv7J6PXH+o/mZwGphRjydQKBgQDk2ujy4F1k6OD4+DfZKsYF9QfiIpNnBomN\n"
                + "qj5TrnmXChbXTJaGQPHOQlOmTWlYv5FSVNMPjsflIH/t5WEbVPwIWZVWToyR3n0C\n"
                + "xCh1GfjLvaE4T9MDI4MFrnef0/wsWegxeo/76ZUqJFZPvq3wEnD4jyC1u3whd0/M\n"
                + "JFrHC2RDhwKBgGnu7CVXnA1Ck3KBIg+zc1k1exogTD1+n/oPfsfv/llJUAUSyjqw\n"
                + "v6zwAxCjF6QT8PXRGgaHwE7Ew2r0tSCDduv2aUM7E+GpSz9fxCUja2VfQ3x0Ikcg\n"
                + "+yRiqrNoVarBz6BhRG6hSOBLD/lCQ4K2P+AaNboKmtQspLlf190bD7gtAoGAF4Ch\n"
                + "J4cmEh2VEkDMS+Wk6ya9ygPp4YlzO8bT1QaxveznXyjcQl5HLwrgBzcZxbTlH1DB\n"
                + "s3wSyK+jVbL5mwH8bh0l53sZr4agvCi7ISPZhPR8VYmn9MWbVdb+bZCgM3Vd+tL7\n"
                + "Ai3G6yprJ1SeB4n4xeFCABwwvPfylGzPCf4XDTkCgYEAiksAv52h244hCgZynxCm\n"
                + "90LIZpw3PqonW6gUblWGHXYgeBCfCwS8G7i3mluh9Iv/zVt8y7RnwNTAVXkZLo+s\n"
                + "3WWzSurZUixqaOHJ3RmLO0jB1MkQnuwyEb5LHb3+CrbePxrLg00yWiLRgqd5joOR\n"
                + "0pqiM3aXGjT8LWXSl6qOeUE=\n"
                + "-----END PRIVATE KEY-----";
    }
}
