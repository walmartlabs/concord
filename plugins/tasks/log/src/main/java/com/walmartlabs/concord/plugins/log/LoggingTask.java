package com.walmartlabs.concord.plugins.log;

import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("log")
public class LoggingTask implements Task {

    public static final Logger log = LoggerFactory.getLogger(LoggingTask.class);

    public void info(String s) {
        log.info(s);
    }

    @Deprecated
    public void info(String logName, String s) {
        log.info("{} - {}", logName, s);
    }

    public void warn(String s) {
        log.warn(s);
    }

    @Deprecated
    public void warn(String logName, String s) {
        log.warn("{} - {}", logName, s);
    }

    public void error(String s) {
        log.error(s);
    }

    public void error(String logName, String s) {
        log.error("{} - {}", logName, s);
    }

    public void call(String s) {
        log.info(s);
    }
}
