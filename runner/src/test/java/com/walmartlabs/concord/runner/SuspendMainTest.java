package com.walmartlabs.concord.runner;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.common.format.AutoParser;
import com.walmartlabs.concord.plugins.yaml2.YamlParserProvider;
import com.walmartlabs.concord.runner.engine.EngineFactory;
import com.walmartlabs.concord.runner.engine.NamedTaskRegistry;
import org.junit.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class SuspendMainTest {

    @Test
    public void test() throws Exception {
        TestBean testBean = spy(new TestBean());

        // ---

        AutoParser autoParser = new AutoParser(new YamlParserProvider().get());
        NamedTaskRegistry taskRegistry = new NamedTaskRegistry(testBean);
        EngineFactory engineFactory = new EngineFactory(taskRegistry);

        // ---

        String instanceId = UUID.randomUUID().toString();
        System.setProperty("instanceId", instanceId);

        URI baseDir = SuspendMainTest.class.getResource("suspend").toURI();
        Path tmpDir = Files.createTempDirectory("test");
        IOUtils.copy(Paths.get(baseDir), tmpDir);
        System.setProperty("user.dir", tmpDir.toString());

        Main main = new Main(autoParser, engineFactory);
        main.run();

        // ---

        verify(testBean, times(1)).call(eq("aaa"));
        verify(testBean, times(0)).call(eq("bbb"));

        // ---

        // TODO constants
        Path evFile = tmpDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME).resolve("_state").resolve("_event");
        Files.write(evFile, "ev1".getBytes());

        main.run();

        // ---

        verify(testBean, times(1)).call(eq("bbb"));
    }

    public static class TestBean implements Task {

        @Override
        public String getKey() {
            return "testBean";
        }

        public void call(Object arg) {
        }
    }
}
