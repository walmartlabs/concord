package com.walmartlabs.concord.agentoperator;

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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class PodUtils {

    private static final Logger log = LoggerFactory.getLogger(PodUtils.class);

    public static Output exec(KubernetesClient client, String podName, String containerName, String... cmd) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Listener l = new Listener();
        try (ExecWatch w = client.pods().withName(podName)
                .inContainer(containerName)
                .writingOutput(stdout)
                .writingError(stderr)
                .usingListener(l)
                .exec(cmd)) {

            l.await();
        } catch (Exception e) {
            log.error("exec ['{}', '{}'] -> error while executing '{}': {}", podName, containerName, cmd, e.getMessage());
            throw e;
        }

        return new Output(stdout.toString(), stderr.toString());
    }

    public static void applyTag(KubernetesClient client, String podName, String tagName, String tagValue) {
        Pod pod = client.pods().withName(podName).get();
        if (pod == null) {
            log.warn("['{}']: apply tag ['{}': '{}'] -> pod doesn't exist, nothing to do", podName, tagName, tagValue);
            return;
        }

        Map<String, String> labels = pod.getMetadata().getLabels();
        if (labels.containsKey(tagName)) {
            return;
        }

        try {
            labels.put(tagName, tagValue);
            client.pods().withName(podName).patch(pod);
            log.info("['{}']: apply tag ['{}': '{}'] -> done", podName, tagName, tagValue);
        } catch (KubernetesClientException e) {
            if (e.getCode() == 404) {
                log.warn("['{}']: apply tag ['{}': '{}'] -> pod doesn't exist, nothing to do", podName, tagName, tagValue);
            }
        }
    }

    public static class Output {

        private final String stdout;
        private final String stderr;

        private Output(String stdout, String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }
    }

    private static class Listener implements ExecListener {

        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onOpen(Response response) {
        }

        @Override
        public void onFailure(Throwable t, Response response) {
            latch.countDown();
        }

        @Override
        public void onClose(int code, String reason) {
            latch.countDown();
        }

        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private PodUtils() {
    }
}
