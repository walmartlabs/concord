package com.walmartlabs.concord.runtime.v2.runner;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DockerServiceTest {

    @Test
    void testSanitizeImageAcceptsValidReferences() {
        assertDoesNotThrow(() -> DefaultDockerService.validateDockerImage("alpine"));
        assertDoesNotThrow(() -> DefaultDockerService.validateDockerImage("docker1.io/library/alpine:3.20"));
        assertDoesNotThrow(() -> DefaultDockerService.validateDockerImage("ghcr.io/acme/build-image:release-2026.08.10"));
        assertDoesNotThrow(() -> DefaultDockerService.validateDockerImage("localhost:5000/ns/app@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    }

    @Test
    void testSanitizeImageRejectsInvalidReferences() {
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage(""));
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage(" alpine"));
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage("alpine; touch /tmp/pwned"));
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage("repo' && whoami && '"));
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage("ghcr.io/acme/app:tag\nuname -a"));
        assertThrows(IllegalArgumentException.class, () -> DefaultDockerService.validateDockerImage("alpine'; id > /tmp/id_out.txt; echo '"));
    }
}
