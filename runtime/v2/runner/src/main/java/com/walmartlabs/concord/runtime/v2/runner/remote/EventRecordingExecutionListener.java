package com.walmartlabs.concord.runtime.v2.runner.remote;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.ProcessDefinitionUtils;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.vm.StepCommand;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

public class EventRecordingExecutionListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(EventRecordingExecutionListener.class);

    private final ProcessEventsApi eventsApi;
    private final InstanceId processInstanceId;
    private final EventConfiguration eventConfiguration;

    @Inject
    public EventRecordingExecutionListener(ApiClient apiClient, InstanceId processInstanceId, ProcessConfiguration processConfiguration) {
        this.eventsApi = new ProcessEventsApi(apiClient);
        this.processInstanceId = processInstanceId;
        this.eventConfiguration = processConfiguration.events();
    }

    @Override
    public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (!eventConfiguration.recordEvents()) {
            return Result.CONTINUE;
        }

        // TODO consider using marker interfaces to determine which step/command should produce ELEMENT events

        if (!(cmd instanceof StepCommand)) {
            return Result.CONTINUE;
        }

        StepCommand<?> s = (StepCommand<?>) cmd;
        if (s.getStep() instanceof TaskCall || s.getStep() instanceof Expression) {
            return Result.CONTINUE;
        }

        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);
        Location loc = s.getStep().getLocation();

        Map<String, Object> m = new HashMap<>();
        m.put("processDefinitionId", ProcessDefinitionUtils.getCurrentFlowName(pd, s.getStep()));
        m.put("fileName", loc.fileName());
        m.put("line", loc.lineNum());
        m.put("column", loc.column());
        m.put("description", getDescription(s.getStep()));
        m.put("correlationId", s.getCorrelationId());
        if (threadId.id() != 0) {
            m.put("threadId", threadId.id());
        }

        ProcessEventRequest req = new ProcessEventRequest();
        req.setEventType("ELEMENT"); // TODO constants
        req.setData(m);
        req.setEventDate(Instant.now().atOffset(ZoneOffset.UTC));

        try {
            eventsApi.event(processInstanceId.getValue(), req);
        } catch (ApiException e) {
            log.warn("afterCommand [{}] -> error while sending an event to the server: {}", cmd, e.getMessage());
        }

        return Result.CONTINUE;
    }

    private static String getDescription(Step step) {
        // TODO: add 'description' into step? so we will not miss description for new steps...
        if (step instanceof FlowCall) {
            return "Flow call: " + ((FlowCall) step).getFlowName();
        } else if (step instanceof Expression) {
            return "Expression: " + ((Expression) step).getExpr();
        } else if (step instanceof ScriptCall) {
            return "Script: " + ((ScriptCall) step).getLanguageOrRef();
        } else if (step instanceof IfStep) {
            return "Check: " + ((IfStep) step).getExpression();
        } else if (step instanceof SwitchStep) {
            return "Switch: " + ((SwitchStep) step).getExpression();
        } else if (step instanceof SetVariablesStep) {
            return "Set variables";
        } else if (step instanceof Checkpoint) {
            return "Checkpoint: " + ((Checkpoint) step).getName();
        } else if (step instanceof FormCall) {
            return "Form call: " + ((FormCall) step).getName();
        } else if (step instanceof GroupOfSteps) {
            return "Group of steps";
        } else if (step instanceof ParallelBlock) {
            return "Parallel block";
        } else if (step instanceof ExitStep) {
            return "Exit";
        } else if (step instanceof ReturnStep) {
            return "Return";
        }

        return step.getClass().getName();
    }
}
