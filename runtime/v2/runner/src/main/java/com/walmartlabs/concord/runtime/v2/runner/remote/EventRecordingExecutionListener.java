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

import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.ProcessDefinitionUtils;
import com.walmartlabs.concord.runtime.v2.model.*;
import com.walmartlabs.concord.runtime.v2.runner.EventReportingService;
import com.walmartlabs.concord.runtime.v2.runner.vm.ElementEventProducer;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

public class EventRecordingExecutionListener implements ExecutionListener {

    private final EventConfiguration eventConfiguration;
    private final EventReportingService eventReportingService;

    @Inject
    public EventRecordingExecutionListener(ProcessConfiguration processConfiguration,
                                           EventReportingService eventReportingService) {
        this.eventConfiguration = processConfiguration.events();
        this.eventReportingService = eventReportingService;
    }

    @Override
    public Result afterCommand(Runtime runtime, VM vm, State state, ThreadId threadId, Command cmd) {
        if (!eventConfiguration.recordEvents()) {
            return Result.CONTINUE;
        }

        // TODO consider using marker interfaces to determine which step/command should produce ELEMENT events

        if (!(cmd instanceof ElementEventProducer s)) {
            return Result.CONTINUE;
        }

        ProcessDefinition pd = runtime.getService(ProcessDefinition.class);
        Location loc = s.getStep().getLocation();

        Map<String, Object> m = new HashMap<>();
        m.put("processDefinitionId", ProcessDefinitionUtils.getCurrentFlowName(pd, s.getStep()));
        m.put("fileName", loc.fileName());
        m.put("line", loc.lineNum());
        m.put("column", loc.column());
        m.put("description", s.getDescription(state, threadId));
        m.put("correlationId", s.getCorrelationId());
        if (threadId.id() != 0) {
            m.put("threadId", threadId.id());
        }

        ProcessEventRequest req = new ProcessEventRequest();
        req.setEventType("ELEMENT"); // TODO constants
        req.setData(m);
        req.setEventDate(Instant.now().atOffset(ZoneOffset.UTC));

        eventReportingService.report(req);

        return Result.CONTINUE;
    }
}
