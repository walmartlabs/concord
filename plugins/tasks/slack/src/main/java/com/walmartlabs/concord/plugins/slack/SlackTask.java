package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.sdk.*;
import io.takari.bpm.api.BpmnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named("slack")
public class SlackTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SlackTask.class);

    private final SlackService slackService;

    @Inject
    public SlackTask(RpcClient rpcClient) {
        this.slackService = rpcClient.getSlackService();
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
            slackService.notify(instanceId, channelId, text);
            log.info("call ['{}', '{}'] -> done", channelId, text);
        } catch (Exception e) {
            log.error("call ['{}', '{}'] -> error", channelId, text, e);
            throw new BpmnError("slackError", e);
        }
    }
}
