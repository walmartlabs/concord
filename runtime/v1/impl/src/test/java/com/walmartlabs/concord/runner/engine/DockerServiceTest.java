package com.walmartlabs.concord.runner.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DockerServiceTest {

    @Test
    void testSanitizeImageAcceptsValidReferences() {
        assertDoesNotThrow(() -> DockerServiceImpl.validateDockerImage("alpine"));
        assertDoesNotThrow(() -> DockerServiceImpl.validateDockerImage("docker1.io/library/alpine:3.20"));
        assertDoesNotThrow(() -> DockerServiceImpl.validateDockerImage("ghcr.io/acme/build-image:release-2026.08.10"));
        assertDoesNotThrow(() -> DockerServiceImpl.validateDockerImage("localhost:5000/ns/app@sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));
    }

    @Test
    void testSanitizeImageRejectsInvalidReferences() {
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage(""));
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage(" alpine"));
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage("alpine; touch /tmp/pwned"));
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage("repo' && whoami && '"));
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage("ghcr.io/acme/app:tag\nuname -a"));
        assertThrows(IllegalArgumentException.class, () -> DockerServiceImpl.validateDockerImage("alpine'; id > /tmp/id_out.txt; echo '"));
    }
}
