package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("slack")
public class SlackTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    private final RpcClient rpc;

    @Inject
    public SlackTask(RpcClient rpc) {
        this.rpc = rpc;
    }

    public void call(String channelId, String text) throws Exception {
        try {
            rpc.getSlackService().notify(channelId, text);
            log.info("call ['{}', '{}'] -> done", channelId, text);
        } catch (Exception e) {
            log.error("call ['{}', '{}'] -> error", channelId, text, e);
            throw new BpmnError("slackError", e);
        }
    }
}
