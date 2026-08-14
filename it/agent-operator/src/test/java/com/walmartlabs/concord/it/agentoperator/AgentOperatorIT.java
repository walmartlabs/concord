package com.walmartlabs.concord.it.agentoperator;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import ca.ibodrov.concord.testcontainers.Payload;
import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.ProcessEntry;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deploys the Concord Agent Operator into a k3s cluster running in a testcontainer,
 * creates an AgentPool resource and verifies that:
 * <ul>
 *     <li>the operator creates the pool's ConfigMap and agent Pod;</li>
 *     <li>the agent in the Pod connects to the Concord Server;</li>
 *     <li>a process with matching agent requirements is dispatched to the pool and finishes successfully.</li>
 * </ul>
 */
public class AgentOperatorIT {

    private static final Logger log = LoggerFactory.getLogger(AgentOperatorIT.class);

    private static final String NAMESPACE = "default";
    private static final String POOL_NAME = "test-pool";
    private static final String AGENT_FLAVOR = "k8s-it";

    private static final String SERVER_IMAGE = System.getProperty("server.image", "walmartlabs/concord-server:latest");
    private static final String AGENT_IMAGE = System.getProperty("agent.image", "walmartlabs/concord-agent:latest");
    private static final String OPERATOR_IMAGE = System.getProperty("operator.image", "walmartlabs/concord-agent-operator:latest");
    private static final String K3S_IMAGE = System.getProperty("k3s.image", "rancher/k3s:v1.31.4-k3s1");

    /**
     * Agent's API key. Must match the "defaultAgentToken" value seeded by the server (see the extra
     * configuration below). The k8s agent pods use the same value to authenticate against the server.
     */
    private static final String AGENT_TOKEN = randomToken();

    private static final Path DEPLOY_DIR = Paths.get("..", "..", "agent-operator", "deploy");

    private static final AtomicReference<Container<?>> serverContainer = new AtomicReference<>();

    @RegisterExtension
    public static final ConcordRule concord = new ConcordRule()
            .dbImage(System.getProperty("db.image", "library/postgres:14"))
            .serverImage(SERVER_IMAGE)
            .agentImage(AGENT_IMAGE)
            // the pool deployed into k3s is the only agent in this test
            .startAgent(false)
            .pullPolicy(PullPolicy.defaultPolicy())
            .streamServerLogs(true)
            .extraConfigurationSupplier(() -> """
                    concord-server {
                        db.changeLogParameters.defaultAgentToken = "%s"
                        queue {
                            enqueuePollInterval = "250 milliseconds"
                            dispatcher {
                                pollDelay = "250 milliseconds"
                            }
                        }
                    }
                    concord-agent {
                        server.apiKey = "%s"
                    }
                    """.formatted(AGENT_TOKEN, AGENT_TOKEN))
            .containerListener(new ContainerListener() {
                @Override
                public void afterStart(ContainerType type, Container<?> container) {
                    if (type == ContainerType.SERVER) {
                        serverContainer.set(container);
                    }
                }
            });

    private static K3sContainer k3s;
    private static KubernetesClient k8s;

    /**
     * Concord Server URL reachable from the pods running in k3s.
     */
    private static String serverUrl;

    @BeforeAll
    public static void setUpK3sAndOperator() throws Exception {
        var server = serverContainer.get();
        assertNotNull(server, "Concord Server container is not available");

        var network = ((GenericContainer<?>) server).getNetwork();
        assertNotNull(network, "Concord Server container has no network");

        // pod traffic is masqueraded through the k3s node container, so the raw IP of the server
        // container on the shared network is reachable from the pods (unlike Docker network aliases,
        // which the pods can't resolve)
        var serverIp = server.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next().getIpAddress();
        assertNotNull(serverIp, "Can't determine the Concord Server container IP");
        serverUrl = "http://" + serverIp + ":8001";
        log.info("Concord Server URL for k3s pods: {}", serverUrl);

        k3s = new K3sContainer(DockerImageName.parse(K3S_IMAGE))
                .withNetwork(network)
                .withNetworkAliases("k3s");
        k3s.start();

        importImagesIntoK3s(AGENT_IMAGE, OPERATOR_IMAGE);

        k8s = new KubernetesClientBuilder()
                .withConfig(Config.fromKubeconfig(k3s.getKubeConfigYaml()))
                .build();

        applyManifest("service_account.yml", null);
        applyManifest("cluster_role.yml", null);
        applyManifest("cluster_role_binding.yml", null);
        applyManifest("crds/agentpools.concord.walmartlabs.com-v1.yml", null);
        waitForCrd();

        applyManifest("operator.yml", s -> s
                .replace("walmartlabs/concord-agent-operator:latest", OPERATOR_IMAGE)
                .replace("http://host.minikube.internal:8001", serverUrl)
                .replace("...API token...", concord.environment().apiToken()));

        createAgentPool();
        waitForAgentPod();
    }

    @AfterAll
    public static void tearDown() {
        if (k8s != null) {
            k8s.close();
        }
        if (k3s != null) {
            k3s.stop();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    public void processRunsOnPoolAgent() throws Exception {
        // the operator should have created the pool's ConfigMap
        var cfg = k8s.configMaps().inNamespace(NAMESPACE).withName(POOL_NAME + "-cfg").get();
        assertNotNull(cfg, "the operator should create the " + POOL_NAME + "-cfg ConfigMap");
        assertTrue(cfg.getData().containsKey("agent.conf"), "the ConfigMap should contain agent.conf");

        // ...and the agent pod (readiness is verified in setUpK3sAndOperator)
        var pods = k8s.pods().inNamespace(NAMESPACE).withLabel("poolName", POOL_NAME).list().getItems();
        assertEquals(1, pods.size(), "the operator should create a single agent pod");
        log.info("Agent pod: {}", pods.get(0).getMetadata().getName());

        // submit a process that can only run on the pool's agent (the default agent container is not running)
        var payload = new Payload()
                .concordYml("""
                        configuration:
                          runtime: "concord-v2"
                          requirements:
                            agent:
                              flavor: "%s"
                        flows:
                          default:
                            - log: "Hello from a k8s agent!"
                        """.formatted(AGENT_FLAVOR));

        var proc = concord.processes().start(payload);

        try {
            var entry = proc.expectStatus(ProcessEntry.StatusEnum.FINISHED);
            assertEquals(ProcessEntry.StatusEnum.FINISHED, entry.getStatus());
        } catch (Exception e) {
            logDiagnostics(proc);
            throw e;
        }

        proc.assertLog(".*Hello from a k8s agent!.*");
    }

    private static void applyManifest(String name, UnaryOperator<String> transform) throws IOException {
        var content = Files.readString(DEPLOY_DIR.resolve(name));
        if (transform != null) {
            content = transform.apply(content);
        }
        try (var in = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            k8s.load(in).inNamespace(NAMESPACE).createOrReplace();
        }
    }

    private static void waitForCrd() {
        k8s.apiextensions().v1().customResourceDefinitions()
                .withName("agentpools.concord.walmartlabs.com")
                .waitUntilCondition(crd -> crd != null
                        && crd.getStatus() != null
                        && crd.getStatus().getConditions() != null
                        && crd.getStatus().getConditions().stream()
                        .anyMatch(c -> "Established".equals(c.getType()) && "True".equals(c.getStatus())),
                        60, TimeUnit.SECONDS);
    }

    private static void createAgentPool() throws IOException {
        var cr = resourceToString("agentpool.yml")
                .replace("%%serverApiBaseUrl%%", serverUrl)
                .replace("%%serverWebsocketUrl%%", serverUrl.replace("http://", "ws://") + "/websocket")
                .replace("%%agentToken%%", AGENT_TOKEN)
                .replace("%%agentImage%%", AGENT_IMAGE);

        try (var in = new ByteArrayInputStream(cr.getBytes(StandardCharsets.UTF_8))) {
            k8s.resource(in).inNamespace(NAMESPACE).createOrReplace();
        }
    }

    private static void waitForAgentPod() {
        try {
            k8s.pods().inNamespace(NAMESPACE)
                    .withLabel("poolName", POOL_NAME)
                    .waitUntilCondition(AgentOperatorIT::isReady, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            dumpPodLogs("concord-agent-operator", "name", "concord-agent-operator");
            dumpPodLogs("agent", "poolName", POOL_NAME);
            throw e;
        }
    }

    private static boolean isReady(Pod pod) {
        if (pod == null || pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return false;
        }
        var statuses = pod.getStatus().getContainerStatuses();
        return !statuses.isEmpty() && statuses.stream().allMatch(ContainerStatus::getReady);
    }

    private static void logDiagnostics(ConcordProcess proc) {
        try {
            log.info("Process log:\n{}", new String(proc.getLog(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Can't fetch the process log: {}", e.getMessage());
        }
        dumpPodLogs("agent", "poolName", POOL_NAME);
    }

    private static void dumpPodLogs(String container, String labelKey, String labelValue) {
        try {
            k8s.pods().inNamespace(NAMESPACE).withLabel(labelKey, labelValue).list().getItems()
                    .forEach(pod -> {
                        var name = pod.getMetadata().getName();
                        try {
                            var podLog = k8s.pods().inNamespace(NAMESPACE).withName(name)
                                    .inContainer(container).getLog();
                            log.info("Log of {}/{}:\n{}", name, container, podLog);
                        } catch (Exception e) {
                            log.warn("Can't fetch the log of {}/{}: {}", name, container, e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.warn("Can't list pods with {}={}: {}", labelKey, labelValue, e.getMessage());
        }
    }

    /**
     * Imports images from the host's Docker daemon into the k3s containerd store,
     * so that pods can use locally built images ({@code imagePullPolicy: Never}).
     */
    private static void importImagesIntoK3s(String... images) throws Exception {
        log.info("Importing images into k3s: {}", Arrays.asList(images));

        var saveCmd = new String[images.length + 2];
        saveCmd[0] = "docker";
        saveCmd[1] = "save";
        System.arraycopy(images, 0, saveCmd, 2, images.length);

        var importCmd = new String[]{"docker", "exec", "-i", k3s.getContainerId(),
                "ctr", "-n", "k8s.io", "images", "import", "-"};

        var save = new ProcessBuilder(saveCmd).start();
        var importProc = new ProcessBuilder(importCmd).start();

        var saveStderr = gobble(save.getErrorStream());
        var importStdout = gobble(importProc.getInputStream());
        var importStderr = gobble(importProc.getErrorStream());

        var pump = new Thread(() -> {
            try (var in = save.getInputStream()) {
                in.transferTo(importProc.getOutputStream());
                importProc.getOutputStream().close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        pump.start();

        var saveRc = save.waitFor();
        pump.join(TimeUnit.MINUTES.toMillis(10));
        var importRc = importProc.waitFor();
        saveStderr.join();
        importStdout.join();
        importStderr.join();

        if (saveRc != 0 || importRc != 0) {
            throw new IllegalStateException("Can't import images into k3s: docker save RC=" + saveRc
                    + ", ctr images import RC=" + importRc);
        }
    }

    private static Thread gobble(InputStream in) {
        var t = new Thread(() -> {
            try (in) {
                in.transferTo(OutputStream.nullOutputStream());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static String resourceToString(String name) throws IOException {
        var url = AgentOperatorIT.class.getResource(name);
        assertNotNull(url, "can't find '" + name + "'");
        try (var in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String randomToken() {
        byte[] ab = new byte[16];
        ThreadLocalRandom.current().nextBytes(ab);
        return Base64.getEncoder().withoutPadding().encodeToString(ab);
    }
}
