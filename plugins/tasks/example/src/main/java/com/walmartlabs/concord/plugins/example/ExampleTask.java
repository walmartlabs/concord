package com.walmartlabs.concord.plugins.example;

import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("example")
public class ExampleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ExampleTask.class);

    @InjectVariable("execution")
    private ExecutionContext context;

    public void hello() {
        log.info("Hello!");
    }

    public void hello(String k) {
        Object o = context.getVariable(k);
        log.info("Hello, {}!", o);
    }

    public void helloButLouder(@InjectVariable("myName") String name) {
        log.info("Hello, {}!!!", name);
    }
}
