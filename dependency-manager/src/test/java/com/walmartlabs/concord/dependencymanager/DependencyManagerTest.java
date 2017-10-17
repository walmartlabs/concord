package com.walmartlabs.concord.dependencymanager;

import org.junit.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class DependencyManagerTest {

    @Test(timeout = 30000)
    public void test() throws Exception {
        Path tmpDir = Files.createTempDirectory("test");
        URI uriA = new URI("mvn://com.walmartlabs.concord:concord-project-model:0.44.0?scope=runtime");
        URI uriB = new URI("mvn://com.walmartlabs.concord:concord-project-model:0.43.0?scope=runtime");

        DependencyManager m = new DependencyManager(tmpDir);
        Collection<Path> paths = m.resolve(Arrays.asList(uriA, uriB));
        assertEquals(10, paths.size());
    }
}
