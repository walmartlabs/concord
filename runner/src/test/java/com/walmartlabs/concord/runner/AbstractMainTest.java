package com.walmartlabs.concord.runner;

import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.common.format.AutoParser;
import com.walmartlabs.concord.plugins.yaml2.YamlParserProvider;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.NamedTaskRegistry;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class AbstractMainTest {

    protected Main createMain(String instanceId, String resource, Task... tasks) throws Exception {
        AutoParser autoParser = new AutoParser(new YamlParserProvider().get());
        NamedTaskRegistry taskRegistry = new NamedTaskRegistry(tasks);
        EngineFactory engineFactory = new EngineFactory(taskRegistry);

        System.setProperty("instanceId", instanceId);

        URI baseDir = this.getClass().getResource(resource).toURI();
        Path tmpDir = Files.createTempDirectory("test");
        IOUtils.copy(Paths.get(baseDir), tmpDir);
        System.setProperty("user.dir", tmpDir.toString());

        return new Main(autoParser, engineFactory);
    }
}
