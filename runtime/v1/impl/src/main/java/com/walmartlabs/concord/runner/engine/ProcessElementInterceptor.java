package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.runner.ContextUtils;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.api.interceptors.ExecutionInterceptorAdapter;
import io.takari.bpm.api.interceptors.InterceptorElementEvent;
import io.takari.bpm.model.ExpressionType;
import io.takari.bpm.model.ServiceTask;

import java.util.Collections;
import java.util.UUID;

public class ProcessElementInterceptor extends ExecutionInterceptorAdapter {

    private final ElementEventProcessor eventProcessor;
    private final ProcessMetadataProcessor processMetadataProcessor;

    public ProcessElementInterceptor(ElementEventProcessor eventProcessor, ProcessMetadataProcessor processMetadataProcessor) {
        this.eventProcessor = eventProcessor;
        this.processMetadataProcessor = processMetadataProcessor;
    }

    @Override
    public void onElement(InterceptorElementEvent ev) throws ExecutionException {
        ElementEventProcessor.ElementEvent event = new ElementEventProcessor.ElementEvent(ev.getProcessBusinessKey(),
                ev.getProcessDefinitionId(), ev.getElementId(), ContextUtils.getSessionToken(ev.getVariables()));

        eventProcessor.process(event,
                element -> Collections.emptyMap(),
                element -> !(element instanceof ServiceTask) || ((ServiceTask) element).getType() != ExpressionType.DELEGATE);

        processMetadataProcessor.process(UUID.fromString(ev.getProcessBusinessKey()), ev.getVariables());
    }

    @Override
    public void onFinish(String processBusinessKey) {
        processMetadataProcessor.flush();
    }

    @Override
    public void onFailure(String processBusinessKey, String errorRef) {
        processMetadataProcessor.flush();
    }

    @Override
    public void onSuspend() {
        processMetadataProcessor.flush();
    }
}
