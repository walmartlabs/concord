package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
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

    @Override
    public void execute(Context ctx) throws Exception {
        String instanceId = (String) ctx.getVariable(Constants.Context.TX_ID_KEY);
        String channelId = (String) ctx.getVariable("channelId");
        String text = (String) ctx.getVariable("text");
        call(instanceId, channelId, text);
    }

    public void call(@InjectVariable("txId") String instanceId, String channelId, String text) throws Exception {
        try {
            rpc.getSlackService().notify(instanceId, channelId, text);
            log.info("call ['{}', '{}'] -> done", channelId, text);
        } catch (Exception e) {
            log.error("call ['{}', '{}'] -> error", channelId, text, e);
            throw new BpmnError("slackError", e);
        }
    }
}
