package com.walmartlabs.concord.it.testingserver;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.DefaultApiClientFactory;
import com.walmartlabs.concord.client2.ProcessApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.client2.ProcessEntry.StatusEnum.FINISHED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A test that tests TestingConcordServer and TestingConcordAgent can run together in the same JVM.
 */
public class TestingConcordIT {

    private PostgreSQLContainer<?> db;
    private TestingConcordServer concordServer;
    private TestingConcordAgent concordAgent;

    @BeforeEach
    public void setUp() throws Exception {
        db = new PostgreSQLContainer<>("postgres:15-alpine");
        db.start();

        int apiPort = getFreePort();
        concordServer = new TestingConcordServer(db, apiPort, Map.of(), List.of());
        concordServer.start();

        concordAgent = new TestingConcordAgent(concordServer);
        concordAgent.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (concordAgent != null) {
            concordAgent.close();
            concordAgent = null;
        }

        if (concordServer != null) {
            concordServer.close();
            concordServer = null;
        }

        if (db != null) {
            db.close();
            db = null;
        }
    }

    @Test
    @Timeout(120)
    public void testRunningSimpleProcess() throws Exception {
        var client = new DefaultApiClientFactory(concordServer.getApiBaseUrl())
                .create(ApiClientConfiguration.builder()
                        .apiKey(concordServer.getAdminApiKey())
                        .build());

        var processApi = new ProcessApi(client);
        var response = processApi.startProcess(Map.of("concord.yml", """
                configuration:
                  runtime: "concord-v2"
                flows:
                  default:
                    - log: "Hello!"
                """.getBytes()));
        assertNotNull(response.getInstanceId());

        var process = processApi.waitForCompletion(response.getInstanceId(), Duration.ofSeconds(60).toMillis());
        assertEquals(FINISHED, process.getStatus());
    }

    private static int getFreePort() {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
