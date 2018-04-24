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

import com.walmartlabs.concord.sdk.RpcClient;
import io.takari.bpm.ProcessDefinitionProvider;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.interceptors.ExecutionInterceptorAdapter;
import io.takari.bpm.api.interceptors.InterceptorElementEvent;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;

public class ProcessElementInterceptor extends ExecutionInterceptorAdapter {

    private final ElementEventProcessor eventProcessor;

    public ProcessElementInterceptor(RpcClient rpc, ProcessDefinitionProvider processDefinitionProvider) {
        this.eventProcessor = new ElementEventProcessor(rpc, processDefinitionProvider);
    }

    @Override
    public void onElement(InterceptorElementEvent ev) throws ExecutionException {
        eventProcessor.process(ev.getProcessBusinessKey(), ev.getProcessDefinitionId(), ev.getElementId(),
                element -> null,
                element -> !(element instanceof ServiceTask) || ((ServiceTask) element).getType() != ExpressionType.DELEGATE);
    }
}