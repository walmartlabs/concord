package com.walmartlabs.concord.it.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.TimeUnit;

@Execution(ExecutionMode.CONCURRENT)
public class TryingIT extends Base {

    @RegisterExtension
    public static TestRule rule = new TestRule();

    @Test
    public void testTimeout() throws Exception {
        System.out.println("first");
        Thread.sleep(10_000);
    }

    @Test
    public void testTimeout2() throws Exception {
        System.out.println("second");
        Thread.sleep(10_000);
    }

    public static class TestRule implements BeforeEachCallback, TestExecutionExceptionHandler, AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            System.out.println("afterEach");
        }

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            System.out.println("beforeEach");
        }

        @Override
        public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
            String fileName = context.getTestClass().get().getName() + context.getTestMethod().get().getName() + ".png";
            System.out.println("handleTestExecutionException:" + fileName);
        }
    }
}
