package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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


import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretUtils;
import com.walmartlabs.concord.server.process.Payload;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Named
public class ProcessInfoProcessor implements PayloadProcessor {

    private final SecretStoreConfiguration secretCfg;

    @Inject
    public ProcessInfoProcessor(SecretStoreConfiguration secretCfg) {
        this.secretCfg = secretCfg;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        Map<String, Object> m = createProcessInfo(payload);

        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            req = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) req.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            req.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        args.put(Constants.Request.PROCESS_INFO_KEY, m);

        return chain.process(payload.putHeader(Payload.REQUEST_DATA_MAP, req));
    }

    private Map<String, Object> createProcessInfo(Payload p) {
        Map<String, Object> m = new HashMap<>();
        m.put("sessionKey", createSessionKey(p.getInstanceId()));
        return m;
    }

    private String createSessionKey(UUID instanceId) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        byte[] ab = SecretUtils.encrypt(instanceId.toString().getBytes(), pwd, salt);
        return Base64.getEncoder().encodeToString(ab);
    }
}
