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

import com.google.gson.reflect.TypeToken;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.OkHttpClient;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.*;
import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.lang.reflect.Type;
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
        return request("/api/v1/process", input, StartProcessResponse.class);
    }

    public <T> T request(String uri, Map<String, Object> input, Class<T> entityType) throws ApiException {
        ApiResponse<T> resp = ClientUtils.postData(client, uri, input, entityType);

        int code = resp.getStatusCode();
        if (code < 200 || code >= 300) {
            if (code == 403) {
                throw new ForbiddenException("Forbidden!", resp.getData());
            }

            throw new ApiException("Request error: " + code);
        }

        return resp.getData();
    }

    public SecretOperationResponse postSecret(String orgName, Map<String, Object> input) throws ApiException {
        return request("/api/v1/org/" + orgName + "/secret", input, SecretOperationResponse.class);
    }
    public SecretOperationResponse generateKeyPair(String orgName, String projectName, String name, boolean generatePassword, String storePassword) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("generatePassword", generatePassword);
        m.put("type", SecretEntry.TypeEnum.KEY_PAIR.toString());
        if (storePassword != null) {
            m.put("storePassword", storePassword);
        }

        if (projectName != null && !projectName.isEmpty()) {
            m.put("project", projectName);
        }
        return postSecret(orgName, m);
    }
    public SecretOperationResponse generateKeyPair(String orgName, Set<String> projectNames, Set<UUID> projectIds, String name, boolean generatePassword, String storePassword) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("generatePassword", generatePassword);
        m.put("type", SecretEntry.TypeEnum.KEY_PAIR.toString());
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
    public SecretOperationResponse addPlainSecret(String orgName, String name, String projectName, boolean generatePassword, String storePassword, byte[] secret) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntry.TypeEnum.DATA.toString());
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

    public SecretOperationResponse addPlainSecret(String orgName, String name, Set<String> projectNames, Set<UUID> projectIds, boolean generatePassword, String storePassword, byte[] secret) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntry.TypeEnum.DATA.toString());
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

    public SecretOperationResponse addUsernamePassword(String orgName, String projectName, String name, boolean generatePassword, String storePassword, String username, String password) throws ApiException {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("type", SecretEntry.TypeEnum.USERNAME_PASSWORD.toString());
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

    public byte[] getLog(String logFileName) throws ApiException {
        Set<String> auths = client.getAuthentications().keySet();
        String[] authNames = auths.toArray(new String[0]);

        Call c = client.buildCall("/logs/" + logFileName, "GET", new ArrayList<>(), new ArrayList<>(),
                null, new HashMap<>(), new HashMap<>(), authNames, null);

        Type t = new TypeToken<byte[]>() {
        }.getType();
        return client.<byte[]>execute(c, t).getData();
    }

    public static ProcessEntry waitForStatus(ProcessApi api, UUID instanceId,
                                             ProcessEntry.StatusEnum status, ProcessEntry.StatusEnum... more) throws InterruptedException {
        int retries = 10;

        ProcessEntry pir;
        while (true) {
            try {
                pir = api.get(instanceId);
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

    public static ProcessEntry waitForCompletion(ProcessApi api, UUID instanceId) throws InterruptedException {
        return waitForStatus(api, instanceId, ProcessEntry.StatusEnum.FAILED, ProcessEntry.StatusEnum.FINISHED);
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

    public void waitForLog(String logFileName, @Language("RegExp") String pattern) throws ApiException, IOException, InterruptedException {
        int retries = 5;

        while (true) {
            byte[] ab = getLog(logFileName);
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
        ApiClient c = new ConcordApiClient(baseUrl, new OkHttpClient());
        c.setReadTimeout(60000);
        c.setConnectTimeout(10000);
        c.setWriteTimeout(60000);

        c.addDefaultHeader("X-Concord-Trace-Enabled", "true");

        if (apiKey != null) {
            c.setApiKey(apiKey);
        }

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
