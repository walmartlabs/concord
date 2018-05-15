package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.rpc.RunnerApiClient;
import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class RpcClientImpl implements RpcClient {

    private final RunnerApiClient client;

    @Inject
    public RpcClientImpl(RpcConfiguration cfg) {
        this.client = new RunnerApiClient(cfg.getServerHost(), cfg.getServerPort());
    }

    @Override
     public SecretReaderService getSecretReaderService() {
        return client.getSecretReaderService();
    }

    @Override
    public EventService getEventService() {
        return client.getEventService();
    }
}
