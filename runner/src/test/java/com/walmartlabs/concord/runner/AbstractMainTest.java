package com.walmartlabs.concord.runner;

import com.google.inject.Injector;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.NamedTaskRegistry;
import com.walmartlabs.concord.runner.engine.RpcClient;
import com.walmartlabs.concord.runner.engine.TaskClassHolder;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.mock;

public abstract class AbstractMainTest {

    protected Main createMain(Injector injector, String instanceId, String resource) throws Exception {
        NamedTaskRegistry taskRegistry = new NamedTaskRegistry(injector, null);
        EngineFactory engineFactory = new EngineFactory(taskRegistry, mock(RpcClient.class));

        System.setProperty("instanceId", instanceId);

        URI baseDir = this.getClass().getResource(resource).toURI();
        Path tmpDir = Files.createTempDirectory("test");
        IOUtils.copy(Paths.get(baseDir), tmpDir);
        System.setProperty("user.dir", tmpDir.toString());

        return new Main(engineFactory);
    }
}
