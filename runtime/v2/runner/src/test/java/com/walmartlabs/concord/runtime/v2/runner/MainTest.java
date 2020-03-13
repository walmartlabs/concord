package com.walmartlabs.concord.runtime.v2.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ImmutableProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.DefaultVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.sdk.Constants;
import org.immutables.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class MainTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(WireMockConfiguration.options()
            .dynamicPort());

    private Path workDir;
    private UUID instanceId;
    private String sessionKey;
    private FormService formService;

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("test");

        instanceId = UUID.randomUUID();
        Path instanceIdFile = workDir.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        Files.write(instanceIdFile, instanceId.toString().getBytes());

        sessionKey = "abc"; // TODO random

        Path formsDir = workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);
        formService = new FormService(formsDir);

        wireMock.stubFor(post(urlPathEqualTo("/api/v1/process/" + instanceId + "/status"))
                .willReturn(aResponse()
                        .withStatus(201)));

        wireMock.stubFor(post(urlPathEqualTo("/api/v1/process/" + instanceId + "/event"))
                .willReturn(aResponse()
                        .withStatus(201)));
    }

    @After
    public void tearDown() throws IOException {
        if (workDir != null) {
            IOUtils.deleteRecursively(workDir);
        }
    }

    @Test
    public void test() throws Exception {
        deploy("hello");

        save(newProcessConfiguration()
                .putArguments("name", "Concord")
                .putDefaultTaskVariables("testDefaults", Collections.singletonMap("a", "a-value"))
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, Concord!.*");
        assertLog(log, ".*" + Pattern.quote("defaultsMap:{a=a-value}") + ".*");
        assertLog(log, ".*" + Pattern.quote("defaultsTyped:Defaults{a=a-value}") + ".*");

        verify(postRequestedFor(urlPathEqualTo("/api/v1/process/" + instanceId + "/status"))
                .withRequestBody(equalTo("RUNNING")));
    }

    @Test
    public void testForm() throws Exception {
        deploy("form");

        save(newProcessConfiguration()
                .build());

        byte[] log = start();
        assertLog(log, ".*Before.*");

        List<Form> forms = formService.list();
        assertEquals(1, forms.size());

        Form myForm = forms.get(0);
        assertEquals("myForm", myForm.name());

        // resume the process using the saved form

        Map<String, Object> data = new HashMap<>();
        data.put("fullName", "John Smith");
        data.put("age", 33);

        log = resume(myForm.eventName(), Collections.singletonMap("arguments", Collections.singletonMap("myForm", data)));
        assertLog(log, ".*After.*John Smith.*");
    }

    private void deploy(String resource) throws URISyntaxException, IOException {
        Path src = Paths.get(MainTest.class.getResource(resource).toURI());
        IOUtils.copy(src, workDir);
    }

    private void save(ProcessConfiguration cfg) throws IOException {
        Path p = workDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        try (OutputStream out = Files.newOutputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            om.writeValue(out, cfg);
        }
    }

    private byte[] start() throws Exception {
        return run();
    }

    private byte[] resume(String eventName, Map<String, Object> cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        saveProcessConfiguration(cfg);
        return run();
    }

    private byte[] run() throws Exception {
        // the runner's configuration file
        Path runnnerCfgFile = createRunnerCfgFile();

        // the session key to talk to the API
        saveSessionKey();

        PrintStream oldOut = System.out;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);

        try {
            System.setProperty("user.dir", workDir.toAbsolutePath().toString());
            Main.main(new String[]{runnnerCfgFile.toAbsolutePath().toString()});
        } finally {
            out.flush();
            System.setOut(oldOut);
        }

        byte[] ab = baos.toByteArray();
        System.out.write(ab, 0, ab.length);

        return ab;
    }

    private ImmutableProcessConfiguration.Builder newProcessConfiguration() {
        Map<String, Object> m = new HashMap<>();
        m.put("txId", instanceId.toString());
        m.put("processInfo", Collections.singletonMap("sessionKey", sessionKey));

        return ProcessConfiguration.builder()
                .arguments(m);
    }

    private Path createRunnerCfgFile() throws IOException {
        Path dst = Files.createTempFile("runner", ".json");
        try (OutputStream out = Files.newOutputStream(dst)) {
            ObjectMapper om = new ObjectMapper();
            om.writeValue(out, RunnerConfiguration.builder()
                    .agentId(UUID.randomUUID().toString())
                    .api(ApiConfiguration.builder()
                            .baseUrl("http://localhost:" + wireMock.port())
                            .build())
                    .build());
        }
        return dst;
    }

    private void saveSessionKey() throws IOException {
        Path dst = workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(Constants.Files.SESSION_TOKEN_FILE_NAME);

        if (!Files.exists(dst.getParent())) {
            Files.createDirectories(dst.getParent());
        }

        Files.write(dst, sessionKey.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void saveProcessConfiguration(Map<String, Object> args) throws IOException {
        if (args == null || args.isEmpty()) {
            return;
        }

        Path dst = workDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        try (OutputStream out = Files.newOutputStream(dst)) {
            new ObjectMapper().writeValue(out, args);
        }
    }

    private static void assertLog(byte[] ab, String pattern) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(ab);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bais))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(pattern)) {
                    return;
                }
            }

            fail("Expected a log entry: " + pattern + ", got: \n" + new String(ab));
        }
    }

    @Named("testDefaults")
    static class TestDefaults implements Task {

        @DefaultVariables("testDefaults")
        Map<String, Object> defaultsMap;

        @DefaultVariables
        Defaults defaultsTyped;

        @Override
        public Serializable execute(TaskContext ctx) {
            System.out.println("defaultsMap:" + defaultsMap);
            System.out.println("defaultsTyped:" + defaultsTyped);
            return null;
        }

        @Value.Immutable
        @JsonSerialize(as = ImmutableDefaults.class)
        @JsonDeserialize(as = ImmutableDefaults.class)
        public interface Defaults {

            String a();

            @Nullable
            String b();
        }
    }
}
