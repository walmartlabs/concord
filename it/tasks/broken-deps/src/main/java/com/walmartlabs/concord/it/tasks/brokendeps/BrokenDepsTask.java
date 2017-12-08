package com.walmartlabs.concord.it.tasks.brokendeps;

import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;

@Named("brokenDeps")
public class BrokenDepsTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(BrokenDepsTask.class);

    @Override
    public void execute(Context ctx) throws Exception {
        log.info("hello!");
    }
}
