package com.walmartlabs.concord.runner;

import com.google.inject.Injector;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.runner.engine.TaskClassHolder;
import com.walmartlabs.concord.sdk.Task;
import org.junit.Test;

import javax.inject.Named;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.mockito.Mockito.*;

public class SuspendMainTest extends AbstractMainTest {

    @Test
    public void test() throws Exception {
        TestBean testBean = spy(new TestBean());

        Injector injector = mock(Injector.class);
        when(injector.getInstance(eq(TestBean.class))).thenReturn(testBean);
        TaskClassHolder.getInstance().register("testBean", TestBean.class);

        String instanceId = UUID.randomUUID().toString();
        Main main = createMain(injector, instanceId, "suspend");
        main.run();

        // ---

        verify(testBean, times(1)).call(eq("aaa"));
        verify(testBean, times(0)).call(eq("bbb"));

        // ---

        Path baseDir = Paths.get(System.getProperty("user.dir"));
        Path evFile = baseDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(InternalConstants.Files.JOB_STATE_DIR_NAME)
                .resolve(InternalConstants.Files.RESUME_MARKER_FILE_NAME);
        Files.write(evFile, "ev1".getBytes());

        main.run();

        // ---

        verify(testBean, times(1)).call(eq("bbb"));
    }

    @Named("testBean")
    public static class TestBean implements Task {

        public void call(Object arg) {
        }
    }
}
