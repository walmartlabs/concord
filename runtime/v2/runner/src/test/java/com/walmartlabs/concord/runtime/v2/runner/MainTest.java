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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.MultipartValuePattern;
import com.google.inject.Injector;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.runtime.common.StateManager;
import com.walmartlabs.concord.runtime.common.cfg.ApiConfiguration;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.runtime.common.injector.WorkingDirectory;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.DefaultVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import com.walmartlabs.concord.runtime.v2.v1.compat.V1CompatModule;
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
import java.util.*;
import java.util.regex.Pattern;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.*;

public class MainTest {

    @Rule
    public WireMockRule wireMock = new WireMockRule(WireMockConfiguration.options()
            .dynamicPort());

    private Path workDir;
    private UUID instanceId;
    private String sessionKey;
    private FormService formService;
    private ProcessConfiguration processConfiguration;

    @Before
    public void setUp() throws IOException {
        workDir = Files.createTempDirectory("test");

        instanceId = UUID.randomUUID();

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

        wireMock.stubFor(post(urlPathEqualTo("/api/v1/process/" + instanceId + "/checkpoint"))
                .willReturn(aResponse()
                        .withStatus(200)));
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

        save(ProcessConfiguration.builder()
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

        save(ProcessConfiguration.builder().build());

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

        log = resume(myForm.eventName(), ProcessConfiguration.builder().arguments(Collections.singletonMap("myForm", data)).build());
        assertLog(log, ".*After.*John Smith.*");
    }

    @Test
    public void testUnknownTask() throws Exception {
        deploy("unknownTask");

        save(ProcessConfiguration.builder().build());

        try {
            start();
            fail("must fail");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("not found: unknown"));
        }
    }

    @Test
    public void testCheckpoints() throws Exception {
        deploy("checkpoints");

        save(ProcessConfiguration.builder()
                .putArguments("name", "Concord")
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, Concord!.*");

        verify(postRequestedFor(urlPathEqualTo("/api/v1/process/" + instanceId + "/status"))
                .withRequestBody(equalTo("RUNNING")));

        MultipartValuePattern checkpoint = aMultipart().withName("name").withBody(equalTo("A")).build();
        verify(postRequestedFor(urlPathEqualTo("/api/v1/process/" + instanceId + "/checkpoint"))
                .withRequestBodyPart(checkpoint));
    }

    @Test
    public void testTaskResultPolicy() throws Exception {
        deploy("taskResultPolicy");

        save(ProcessConfiguration.builder().build());

        try {
            start();
            fail("exception expected");
        } catch (Exception e) {
            assertEquals("Found forbidden tasks", e.getMessage());
        }
    }

    @Test
    public void testTaskInputInterpolate() throws Exception {
        deploy("taskInputInterpolate");

        save(ProcessConfiguration.builder()
                .build());

        byte[] log = start();
        assertLog(log, ".*Hello, " + Pattern.quote("${myFavoriteExpression}") + "!.*");
    }

    private void deploy(String resource) throws URISyntaxException, IOException {
        Path src = Paths.get(MainTest.class.getResource(resource).toURI());
        IOUtils.copy(src, workDir);
    }

    private void save(ProcessConfiguration cfg) {
        this.processConfiguration = ProcessConfiguration.builder().from(cfg)
                .instanceId(instanceId)
                .build();
    }

    private byte[] start() throws Exception {
        return run();
    }

    private byte[] resume(String eventName, ProcessConfiguration cfg) throws Exception {
        StateManager.saveResumeEvent(workDir, eventName); // TODO use interface
        if (cfg != null) {
            save(cfg);
        }
        return run();
    }

    private byte[] run() throws Exception {
        RunnerConfiguration runnerCfg = RunnerConfiguration.builder()
                .agentId(UUID.randomUUID().toString())
                .api(ApiConfiguration.builder()
                        .baseUrl("http://localhost:" + wireMock.port())
                        .sessionToken(sessionKey)
                        .build())
                .build();

        PrintStream oldOut = System.out;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        System.setOut(out);

        try {
            ClassLoader parentClassLoader = Main.class.getClassLoader();
            Injector injector = new InjectorFactory(parentClassLoader,
                    new WorkingDirectory(workDir),
                    runnerCfg,
                    () -> processConfiguration,
                    new DefaultServicesModule(),
                    new V1CompatModule())
                    .create();

            injector.getInstance(Main.class).execute();
        } finally {
            out.flush();
            System.setOut(oldOut);
        }

        byte[] ab = baos.toByteArray();
        System.out.write(ab, 0, ab.length);

        return ab;
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

    @Named("wrapExpression")
    static class WrapExpressionTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) {
            return "${" + ctx.input().get("expression") + "}";
        }
    }

    @Named("testTask")
    static class TestTask implements Task {

        @Override
        public Serializable execute(TaskContext ctx) throws Exception {
            return new HashMap<>(ctx.input());
        }
    }
}
