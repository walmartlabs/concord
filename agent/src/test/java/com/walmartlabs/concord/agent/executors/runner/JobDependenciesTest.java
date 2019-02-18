package com.walmartlabs.concord.agent.executors.runner;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.Test;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobDependenciesTest {

    @Test
    public void test() throws Exception {
        Path payloadDir = Files.createTempDirectory("test");

        Path versionsFile = payloadDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.DEPENDENCY_VERSIONS_FILE_NAME);

        Files.createDirectories(versionsFile.getParent());

        try (InputStream in = JobDependenciesTest.class.getResourceAsStream("versions.properties")) {
            Files.copy(in, versionsFile);
        }

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(Constants.Request.DEPENDENCIES_KEY, Arrays.asList(
                "mvn://aaa:aaa:1.0",
                "mvn://bbb:bbb"
        ));

        RunnerJob j = mock(RunnerJob.class);
        when(j.getCfg()).thenReturn(cfg);
        when(j.getPayloadDir()).thenReturn(payloadDir);

        Collection<URI> uris = JobDependencies.get(j);
        assertEquals(2, uris.size());

        assertContains("mvn://aaa:aaa:1.0", uris);
        assertContains("mvn://bbb:bbb:1.0", uris);
    }

    private static void assertContains(String s, Collection<URI> uris) {
        for (URI u : uris) {
            if (u.toString().equals(s)) {
                return;
            }
        }

        fail("Expected to find " + s);
    }
}
