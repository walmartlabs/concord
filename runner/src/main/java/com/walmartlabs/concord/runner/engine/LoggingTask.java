package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named
public class LoggingTask implements Task {

    @Override
    public String getKey() {
        return "log";
    }

    public void info(String log, String s) {
        LoggerFactory.getLogger(log).info(s);
    }

    public void warn(String log, String s) {
        LoggerFactory.getLogger(log).warn(s);
    }

    public void call(String s) {
        LoggerFactory.getLogger(LoggingTask.class).info(s);
    }
}
