package com.walmartlabs.concord.plugins.log;

import com.walmartlabs.concord.sdk.Task;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("log")
public class LoggingTask implements Task {

    public static final String DEFAULT_LOGGER_NAME = "flow";

    public void info(String s) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).info(s);
    }

    public void info(String log, String s) {
        LoggerFactory.getLogger(log).info(s);
    }

    public void warn(String s) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).warn(s);
    }

    public void warn(String log, String s) {
        LoggerFactory.getLogger(log).warn(s);
    }

    public void error(String s) {
        LoggerFactory.getLogger(DEFAULT_LOGGER_NAME).error(s);
    }

    public void error(String log, String s) {
        LoggerFactory.getLogger(log).error(s);
    }

    public void call(String s) {
        LoggerFactory.getLogger(LoggingTask.class).info(s);
    }
}
