package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.SessionTokenCreator;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Generates and saves the process' session token (key).
 */
@Named
public class SessionTokenProcessor implements PayloadProcessor {

    private final SessionTokenCreator sessionTokenCreator;

    @Inject
    public SessionTokenProcessor(SessionTokenCreator sessionTokenCreator) {
        this.sessionTokenCreator = sessionTokenCreator;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String token = sessionTokenCreator.create(payload.getProcessKey());
        payload = payload.putHeader(Payload.SESSION_TOKEN, token);
        return chain.process(payload);
    }
}
