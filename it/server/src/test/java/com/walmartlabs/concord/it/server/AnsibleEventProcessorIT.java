package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.client.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnsibleEventProcessorIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        URI uri = ProcessIT.class.getResource("ansibleEventProcessor").toURI();
        byte[] payload = archive(uri, ITConstants.DEPENDENCIES_DIR);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        StartProcessResponse spr = start(payload);

        // ---

        ProcessEntry pir = waitForCompletion(processApi, spr.getInstanceId());
        Assertions.assertEquals(ProcessEntry.StatusEnum.FINISHED, pir.getStatus());

        // ---

        AnsibleProcessApi ansibleApi = new AnsibleProcessApi(getApiClient());

        PlaybookEntry playbook = assertPlaybook(ansibleApi, pir.getInstanceId());
        assertEquals("playbook/hello.yml", playbook.getName());
        assertEquals(1L, playbook.getHostsCount().longValue());
        assertEquals(1, playbook.getPlaysCount().intValue());

        PlayInfo play = assertPlay(ansibleApi, pir.getInstanceId(), playbook.getId());
        assertEquals("local", play.getPlayName());
        assertEquals(2L, play.getTaskCount().longValue());
    }

    private static PlaybookEntry assertPlaybook(AnsibleProcessApi ansibleApi, UUID instanceId)  throws Exception {
        List<PlaybookEntry> playbooks = poll(() -> ansibleApi.listPlaybooks(instanceId));
        assertEquals(1, playbooks.size());
        return playbooks.get(0);
    }

    private static PlayInfo assertPlay(AnsibleProcessApi ansibleApi, UUID instanceId, UUID playbookId)  throws Exception {
        List<PlayInfo> plays = poll(() -> ansibleApi.listPlays(instanceId, playbookId));
        assertEquals(1, plays.size());
        return plays.get(0);
    }

    private static <T> List<T> poll(Callable<List<T>> call) throws Exception {
        while (true) {
            List<T> result = call.call();
            if(result != null && !result.isEmpty()) {
                return result;
            }

            Thread.sleep(1000);
        }
    }
}
