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

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class VariablesIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .cfg(ImmutableMap.of("arguments",
                        ImmutableMap.of("nested",
                                ImmutableMap.of(
                                        "y", "cba",
                                        "z", true))))
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("variables").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse spr = start(payload);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*x=123.*", ab);
        assertLog(".*y=abc.*", ab);
        assertLog(".*z=false.*", ab);
        assertLog(".*nested.p=false.*", ab);
        assertLog(".*nested.q=abc.*", ab);
        assertLog(".*var1=var1-value.*", ab);
    }

    @Test
    public void testCrypto() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();
        String secretValue = "secret_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        EncryptValueResponse evr = projectsApi.encrypt(orgName, projectName, secretValue);
        String encryptedValue = evr.getData();

        projectsApi.updateProjectConfiguration(orgName, projectName,
                ImmutableMap.of("arguments",
                        ImmutableMap.of("mySecret", encryptedValue)));

        // ---

        byte[] payload = archive(VariablesIT.class.getResource("crypto").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*" + secretValue + ".*", ab);
    }

    @Test
    public void testArrayInterpolation() throws Exception {
        String varA = "varA_" + System.currentTimeMillis();
        String varB = "varB_" + System.currentTimeMillis();

        byte[] payload = archive(VariablesIT.class.getResource("arrayInterpolation").toURI());

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

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*" + varA + ".*" + varB + ".*", ab);
    }

    @Test
    public void testSetVar() throws Exception {
        byte[] payload = archive(VariablesIT.class.getResource("setVar").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*shouldBeNull: $", ab);
        assertLog(".*nested\\.var: nested\\.var.*", ab);
    }

    @Test
    public void testGetNestedVar() throws Exception {
        byte[] payload = archive(VariablesIT.class.getResource("getVar").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*param1: 1$", ab);
        assertLog(".*defaultValue: 101$", ab);
        assertLog(".*defaultValueFromUnknown: 102$", ab);
    }

    @Test
    public void testSetDependentVars() throws Exception {
        byte[] payload = archive(VariablesIT.class.getResource("setVarNested").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        input.put("arguments.obj.x", "123");
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*obj.x: 123$", ab);
        assertLog(".*obj.name: Concord$", ab);
        assertLog(".*obj.msg: Hello, Concord$", ab);
    }

    @Test
    public void testSetDependentVars2() throws Exception {
        byte[] payload = archive(VariablesIT.class.getResource("setVarNested2").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        byte[] ab = getLog(pir.getInstanceId());
        assertLog(".*event: \\{branch=master\\}$", ab);
        assertLog(".*commitEvent.event: push$", ab);
        assertLog(".*commitEvent.branch: master$", ab);
    }
}
