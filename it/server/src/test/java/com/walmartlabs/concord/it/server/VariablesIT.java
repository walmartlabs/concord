package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.api.org.project.EncryptValueResponse;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.EncryptValueRequest;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class VariablesIT extends AbstractServerIT {

    @Test(timeout = 60000)
    public void test() throws Exception {
        String projectName = "project_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(null, projectName, null, null, null, null,
                ImmutableMap.of("arguments",
                        ImmutableMap.of("nested",
                                ImmutableMap.of(
                                        "y", "cba",
                                        "z", true))),
                null, null, true));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("variables").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());

        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=false.*", ab);
        assertLog(".*nested.p=false.*", ab);
        assertLog(".*nested.q=abc.*", ab);
        assertLog(".*var1=var1-value.*", ab);
    }

    @Test(timeout = 60000)
    public void testCrypto() throws Exception {
        String projectName = "project_" + randomString();
        String secretValue = "secret_" + randomString();

        ProjectResource projectResource = proxy(ProjectResource.class);
        projectResource.createOrUpdate(new ProjectEntry(projectName));

        EncryptValueResponse evr = projectResource.encrypt(projectName, new EncryptValueRequest(secretValue));
        String encryptedValue = DatatypeConverter.printBase64Binary(evr.getData());

        projectResource.updateConfiguration(projectName, ImmutableMap.of("arguments",
                ImmutableMap.of("mySecret", encryptedValue)));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("crypto").toURI());

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(projectName, new ByteArrayInputStream(payload), null, false, null);

        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + secretValue + ".*", ab);
    }

    @Test(timeout = 60000)
    public void testNexus() throws Exception {
        byte[] payload = archive(VariablesIT.class.getResource("nexus").toURI());

        Map<String, Object> req = new HashMap<>();
        req.put("archive", payload);
        req.put("arguments.nexusperf_configuration", "mvn_flood");

        StartProcessResponse spr = start(req);

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*test.duration=60.*", ab);
    }

    @Test(timeout = 60000)
    public void testArrayInterpolation() throws Exception {
        String varA = "varA_" + System.currentTimeMillis();
        String varB = "varB_" + System.currentTimeMillis();

        byte[] payload = archive(VariablesIT.class.getResource("arrayInterpolation").toURI(),
                ITConstants.DEPENDENCIES_DIR);

        Map<String, Object> args = new HashMap<>();
        args.put("varA", varA);
        args.put("varB", varB);

        Map<String, Object> req = new HashMap<>();
        req.put("arguments", args);

        Map<String, Object> input = new HashMap<>();
        input.put("request", req);
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        ProcessEntry pir = waitForCompletion(processResource, spr.getInstanceId());

        byte[] ab = getLog(pir.getLogFileName());
        assertLog(".*" + varA + ".*" + varB + ".*", ab);
    }
}
