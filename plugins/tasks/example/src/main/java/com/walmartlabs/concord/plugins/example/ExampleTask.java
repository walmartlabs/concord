package com.walmartlabs.concord.plugins.example;

import com.walmartlabs.concord.common.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named
public class ExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ExampleTask.class);

    @Override
    public String getKey() {
        return "example";
    }

    public void hello() {
        log.info("Hello!");
    }
}
