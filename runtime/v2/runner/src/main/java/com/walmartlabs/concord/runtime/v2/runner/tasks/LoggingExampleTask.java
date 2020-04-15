package com.walmartlabs.concord.runtime.v2.runner.tasks;

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.TaskContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Named("loggingExample")
public class LoggingExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(LoggingExampleTask.class);

    @Override
    public Serializable execute(TaskContext ctx) throws Exception {
        log.info("Hello, I'm a task!");

        System.out.println("this goes into the stdout");

        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 0; i < 5; i++) {
            final int n = i;
            executor.submit(() -> {
                Logger log = LoggerFactory.getLogger("taskThread" + n);
                log.info("Hey, I'm a task thread #" + n);
            });
        }

        executor.shutdown();
        executor.awaitTermination(100, TimeUnit.SECONDS);

        return null;
    }
}
