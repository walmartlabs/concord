package com.walmartlabs.concord.runner;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.Task;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class FormsMainTest extends AbstractMainTest {

    @Test
    public void test() throws Exception {
        TestBean testBean = spy(new TestBean());

        String instanceId = UUID.randomUUID().toString();
        Main main = createMain(instanceId, "forms", testBean);
        main.run();

        // ---

        verify(testBean, times(1)).call(eq("start"));

        // ---

        Path baseDir = Paths.get(System.getProperty("user.dir"));
        Path evFile = baseDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.JOB_STATE_DIR_NAME)
                .resolve(Constants.RESUME_MARKER_FILE_NAME);
        Files.write(evFile, "myForm".getBytes());

        main.run();

        // ---

        verify(testBean, times(1)).call(eq("test"));
        verify(testBean, times(1)).call(eq("end"));
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
