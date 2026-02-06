package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.it.common.ITConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledIfEnvironmentVariable(named = "SKIP_DOCKER_TESTS", matches = "true", disabledReason = "Requires dockerd listening on a tcp socket. Not available in a typical CI environment")
public class DockerIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("docker").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*DOCKER: Hello, world.*", ab);
    }

    @Test
    public void testOut() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerOut").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*!! Hello, world !!.*", ab);
        assertLog(".*DOCKER: STDERR STILL WORKS.*", ab);
    }

    @Test
    public void testTaskSyntaxOut() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerTaskSyntaxOut").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*!! Hello, world.*", ab);
        assertLog(".*DOCKER: STDERR STILL WORKS.*", ab);
    }

    @Test
    public void testNoLogWithStdOut() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerNoLogWithStdOut").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertNoLog(".*!! Hello, world .*", ab);
        assertNoLog(".*STDERR WORKS !!.*", ab);
    }

    @Test
    public void testLogWithoutStdOut() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerLogWithoutStdOut").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*DOCKER: Hello, world.*", ab);
        assertLog(".*DOCKER: STDERR WORKS.*", ab);
    }

    @Test
    public void testLogWithStdErr() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerLogWithStdErr").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.image", ITConstants.DOCKER_ANSIBLE_IMAGE);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());
        assertNotNull(pir.getLogFileName());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*STDERR: STDERR WORKS.*", ab);
    }

    @Test
    public void testPullRetry() throws Exception {
        byte[] payload = archive(DockerIT.class.getResource("dockerPullRetry").toURI());

        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLogAtLeast(".*Error pulling the image.*", 2, ab);
    }
}
