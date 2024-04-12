package com.walmartlabs.concord.it.runtime.v2;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.ContainerListener;
import ca.ibodrov.concord.testcontainers.ContainerType;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.Testcontainers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.walmartlabs.concord.common.IOUtils.createTempFile;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;

public class TemplateIT extends AbstractTest {

    @RegisterExtension
    public static WireMockExtension rule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .globalTemplating(true))
            .build();

    @RegisterExtension
    public static final ConcordRule concord = ConcordConfiguration
            .configure()
            .containerListener(new ContainerListener() {
                @Override
                public void beforeStart(ContainerType type) {
                    if (type == ContainerType.SERVER) {
                        Testcontainers.exposeHostPorts(rule.getPort());
                    }
                }
            });

    @Test
    public void testTemplate() throws Exception {
        Path templatePath = createTemplate(Paths.get(resource("template")));
        String templateUrl = stubForGetTemplate(templatePath.toAbsolutePath());
        String templateAlias = "template_" + randomString();

        TemplateAliasApi templateAliasResource = new TemplateAliasApi(concord.apiClient());
        templateAliasResource.createOrUpdateTemplate(new TemplateAliasEntry()
                .alias(templateAlias)
                .url(templateUrl));

        // ---

        String orgName = "Default";
        String projectName = "project_" + randomString();
        String myName = "myName_" + randomString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(concord.apiClient());
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.TEMPLATE_KEY, templateAlias);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .cfg(cfg)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("name", myName);

        ConcordProcess proc = concord.processes().start(input);

        // ---

        expectStatus(proc, ProcessEntry.StatusEnum.FINISHED);

        // ---

        proc.assertLog(".*Hello, " + myName + ".*");
    }

    /**
     * Creates a stub for serving a template file
     *
     * @param tPath Path to the local template file
     * @return URL to wiremock location of the template file
     */
    private static String stubForGetTemplate(Path tPath) throws MalformedURLException {
        try (InputStream is = new FileInputStream(tPath.toFile())) {
            rule.stubFor(WireMock.get(urlPathEqualTo(tPath.toString()))
                    .willReturn(WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/octet-stream")
                            .withBody(IOUtils.toByteArray(is))
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to stub for template file download" + e.getMessage());
        }

        return new URL("http", concord.hostAddressAccessibleByContainers(), rule.getPort(), tPath.toString()).toString();
    }

    /**
     * Creates a zip file for using as a template
     *
     * @param templateDir Directory containing template files
     * @return Path to the tempalte zip file
     * @throws IOException when template zip file cannot be created
     */
    private static Path createTemplate(Path templateDir) throws IOException {
        Path tmpZip = createTempFile("runtime-v2Template", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmpZip))) {
            IOUtils.zip(zip, templateDir);
        }

        if (!tmpZip.toFile().setReadable(true, false)) {
            throw new RuntimeException("Cannot set readable permissions for template file: " + tmpZip);
        }

        return tmpZip;
    }
}
