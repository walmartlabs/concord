package com.walmartlabs.concord.server.plugins.eventsink.kafka;

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

import com.walmartlabs.ollie.config.Config;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.Serializable;

public class KafkaEventSinkConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    @Nullable
    @Config("eventSink.kafka.enabled")
    private Boolean enabled;

    @Inject
    @Nullable
    @Config("eventSink.kafka.clientId")
    private String clientId;

    @Inject
    @Nullable
    @Config("eventSink.kafka.bootstrapServers")
    private String bootstrapServers;

    @Inject
    @Nullable
    @Config("eventSink.kafka.processEventsTopic")
    private String processEventsTopic;

    @Inject
    @Nullable
    @Config("eventSink.kafka.processLogsTopic")
    private String processLogsTopic;

    @Inject
    @Nullable
    @Config("eventSink.kafka.auditLogTopic")
    private String auditLogTopic;

    @Nullable
    public Boolean getEnabled() {
        return enabled;
    }

    @Nullable
    public String getClientId() {
        return clientId;
    }

    @Nullable
    public String getBootstrapServers() {
        return bootstrapServers;
    }

    @Nullable
    public String getProcessEventsTopic() {
        return processEventsTopic;
    }

    @Nullable
    public String getProcessLogsTopic() {
        return processLogsTopic;
    }

    @Nullable
    public String getAuditLogTopic() {
        return auditLogTopic;
    }
}
