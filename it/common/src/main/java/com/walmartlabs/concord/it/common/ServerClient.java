package com.walmartlabs.concord.it.common;

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

import com.walmartlabs.concord.client2.*;
import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static org.junit.jupiter.api.Assertions.*;

public class ServerClient {

    /**
     * As defined in server.conf
     */
    public static final String DEFAULT_API_KEY = envApiKey();

    private final ApiClient client;

    public ServerClient(String baseUrl) {
        this.client = createClient(baseUrl, DEFAULT_API_KEY, null);
    }

    public ApiClient getClient() {
        return client;
    }

    public void resetApiKey() {
        setApiKey(DEFAULT_API_KEY);
    }

    public synchronized void setApiKey(String apiKey) {
        this.client.setApiKey(apiKey);
    }

    public void setGithubKey(String githubKey) {
        this.client.addDefaultHeader("X-Hub-Signature", githubKey);
    }

    public StartProcessResponse start(Map<String, Object> input) throws ApiException {
        return new ProcessApi(client).startProcess(input);
    }

    public SecretOperationResponse postSecret(String orgName, Map<String, Object> input) throws ApiException {
        SecretsApi api = new SecretsApi(client);
        return api.createSecret(orgName, input);
    }

    public SecretOperationResponse generateKeyPair(String orgName, String projectName, String name,
                                                   boolean generatePassword,
                                                   String storePassword) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("generatePassword", generatePassword);
        m.put("type", SecretEntryV2.TypeEnum.KEY_PAIR.toString());
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        if (projectName != null && !projectName.isEmpty()) {
            m.put("project", projectName);
        }
        return postSecret(orgName, m);
    }

    public SecretOperationResponse generateKeyPair(String orgName, Set<String> projectNames, Set<UUID> projectIds,
                                                   String name, boolean generatePassword,
                                                   String storePassword) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("generatePassword", generatePassword);
        m.put("type", SecretEntryV2.TypeEnum.KEY_PAIR.toString());
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        if (projectIds != null && !projectIds.isEmpty()) {
            m.put("projectIds", projectIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
        } else if (projectNames != null && !projectNames.isEmpty()) {
            m.put("projects", String.join(",", projectNames));
        }
        return postSecret(orgName, m);
    }

    public SecretOperationResponse addPlainSecret(String orgName, String name, String projectName,
                                                  boolean generatePassword, String storePassword,
                                                  byte[] secret) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntryV2.TypeEnum.DATA.toString());
        m.put("generatePassword", generatePassword);

        if (projectName != null && !projectName.isEmpty()) {
            m.put("project", projectName);
        }
        m.put("data", secret);
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        return postSecret(orgName, m);
    }

    public SecretOperationResponse addPlainSecret(String orgName, String name, Set<String> projectNames,
                                                  Set<UUID> projectIds, boolean generatePassword, String storePassword,
                                                  byte[] secret) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntryV2.TypeEnum.DATA.toString());
        m.put("generatePassword", generatePassword);

        if (projectIds != null && !projectIds.isEmpty()) {
            m.put("projectIds", projectIds.stream().map(UUID::toString).collect(Collectors.joining(",")));
        } else if (projectNames != null && !projectNames.isEmpty()) {
            m.put("projects", String.join(",", projectNames));
        }
        m.put("data", secret);
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        return postSecret(orgName, m);
    }

    public SecretOperationResponse addUsernamePassword(String orgName, String projectName, String name,
                                                       boolean generatePassword, String storePassword, String username,
                                                       String password) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntryV2.TypeEnum.USERNAME_PASSWORD.toString());
        m.put("generatePassword", generatePassword);
        m.put("username", username);
        m.put("password", password);
        if (projectName != null && !projectName.isEmpty()) {
            m.put("project", projectName);
        }
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        return postSecret(orgName, m);
    }

    public byte[] getLog(UUID instanceId) throws ApiException {
        try (InputStream is = new ProcessApi(client).getProcessLog(instanceId, null)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ProcessEntry waitForStatus(ApiClient apiClient, UUID instanceId,
                                             ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum... more) throws InterruptedException {
        int retries = 10;

        ProcessV2Api apiV2 = new ProcessV2Api(apiClient);

        ProcessEntry pir;
        while (true) {
            try {
                pir = apiV2.getProcess(instanceId, Collections.singleton("childrenIds"));
                if (pir.getStatus() == ProcessEntry.StatusEnum.FINISHED || pir.getStatus() == ProcessEntry.StatusEnum.FAILED || pir.getStatus() == ProcessEntry.StatusEnum.CANCELLED) {
                    return pir;
                }

                if (isSame(pir.getStatus(), status, more)) {
                    return pir;
                }
            } catch (ApiException e) {
                if (e.getCode() == 404) {
                    System.out.printf("waitForCompletion ['%s'] -> not found, retrying... (%s)%n", instanceId, retries);
                    if (--retries < 0) {
                        throw new RuntimeException(e);
                    }
                }
            }

            Thread.sleep(500);
        }
    }

    public static ProcessEntry waitForChild(ProcessApi api,
                                            UUID parentInstanceId, ProcessEntry.KindEnum kind,
                                            ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum... more) throws InterruptedException, ApiException {

        int retries = 10;
        while (true) {
            List<ProcessEntry> l = api.listSubprocesses(parentInstanceId, null);
            ProcessEntry e = findByKindAndStatus(l, kind, status, more);
            if (e != null) {
                return e;
            }

            if (--retries < 0) {
                throw new IllegalStateException("Child process not found: " +
                        "kind=" + kind + ", status=" + status + "+" + Arrays.toString(more) + ", " +
                        "got " + l);
            }

            Thread.sleep(3000);
        }
    }

    private static ProcessEntry findByKindAndStatus(Collection<ProcessEntry> c, ProcessEntry.KindEnum kind,
                                                    ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum... more) {

        for (ProcessEntry e : c) {
            if (e.getKind() != kind) {
                continue;
            }

            if (e.getStatus() == status) {
                return e;
            }

            for (ProcessEntry.StatusEnum s : more) {
                if (e.getStatus() == s) {
                    return e;
                }
            }
        }

        return null;
    }

    public static ProcessEntry waitForCompletion(ApiClient apiClient, UUID instanceId) throws InterruptedException {
        return waitForStatus(apiClient, instanceId, ProcessEntry.StatusEnum.FAILED, ProcessEntry.StatusEnum.FINISHED);
    }

    public static void assertLog(@Language("RegExp") String pattern, byte[] ab) throws IOException {
        String msg = "Expected: " + pattern + "\n"
                + "Got: " + new String(ab);
        assertEquals(1, grep(pattern, ab).size(), msg);
    }

    public static void assertNoLog(@Language("RegExp") String pattern, byte[] ab) throws IOException {
        String msg = "Expected: " + pattern + "\n"
                + "Got: " + new String(ab);
        assertEquals(0, grep(pattern, ab).size(), msg);
    }

    public static void assertLog(@Language("RegExp") String pattern, int times, byte[] ab) throws IOException {
        assertEquals(times, grep(pattern, ab).size());
    }

    public static void assertLogAtLeast(@Language("RegExp") String pattern, int times, byte[] ab) throws IOException {
        int matches = grep(pattern, ab).size();
        assertTrue(times <= matches, "Expected to find " + pattern + " at least " + times + " time(s), found only " + matches);
    }

    public void waitForLog(UUID instanceId, @Language("RegExp") String pattern) throws ApiException, IOException, InterruptedException {
        int retries = 5;

        while (true) {
            byte[] ab = getLog(instanceId);
            if (!grep(pattern, ab).isEmpty()) {
                break;
            }

            if (--retries < 0) {
                fail("waitForLog: " + pattern);
            }

            Thread.sleep(500);
        }
    }

    private static boolean isSame(ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum first, ProcessEntry.StatusEnum... more) {
        if (status == first) {
            return true;
        }

        if (more != null) {
            for (ProcessEntry.StatusEnum s : more) {
                if (status == s) {
                    return true;
                }
            }
        }

        return false;
    }

    private static ApiClient createClient(String baseUrl, String apiKey, String gitHubKey) {
        ApiClient c = new DefaultApiClientFactory(baseUrl, Duration.ofMillis(10000))
                .create(ApiClientConfiguration.builder()
                        .apiKey(apiKey)
                        .build());
        c.setReadTimeout(Duration.ofMillis(60000));

        c.addDefaultHeader("X-Concord-Trace-Enabled", "true");

        if (gitHubKey != null) {
            c.addDefaultHeader("X-Hub-Signature", gitHubKey);
        }

        return c;
    }

    private static String envApiKey() {
        String s = System.getenv("IT_DEFAULT_API_KEY");
        if (s == null) {
            throw new IllegalStateException("The default (admin) API key must be configured via IT_DEFAULT_API_KEY environment variable. " +
                    "The value must match the db.changeLogParameters.defaultAdminToken value in the server's configuration file");
        }
        return s;
    }
}
