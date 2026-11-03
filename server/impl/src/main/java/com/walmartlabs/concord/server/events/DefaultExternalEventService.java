package com.walmartlabs.concord.server.events;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.events.ExternalEvent;
import com.walmartlabs.concord.server.sdk.events.ExternalEventService;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.walmartlabs.concord.common.MemoSupplier.memo;

public class DefaultExternalEventService implements ExternalEventService {

    private final TriggersDao triggersDao;
    private final TriggerProcessExecutor executor;
    private final TriggerEventInitiatorResolver initiatorResolver;
    private final UserManager userManager;
    private final AuditLog auditLog;

    @Inject
    public DefaultExternalEventService(TriggersDao triggersDao,
                                       TriggerProcessExecutor executor,
                                       TriggerEventInitiatorResolver initiatorResolver,
                                       UserManager userManager,
                                       AuditLog auditLog) {
        this.triggersDao = triggersDao;
        this.executor = executor;
        this.initiatorResolver = initiatorResolver;
        this.userManager = userManager;
        this.auditLog = auditLog;
    }

    @Override
    public boolean isDisabled(String source) {
        return executor.isDisabled(source);
    }

    @Override
    public void audit(String source, String eventId, Map<String, Object> payload) {
        auditLog.add(AuditObject.EXTERNAL_EVENT, AuditAction.ACCESS)
                .field("source", source)
                .field("eventId", eventId)
                .field("payload", payload)
                .log();
    }

    @Override
    public List<PartialProcessKey> submit(List<ExternalEvent> events) {
        Objects.requireNonNull(events, "events");
        events.forEach(event -> Objects.requireNonNull(event, "event"));

        List<MatchedEvent> matches = new ArrayList<>();
        for (ExternalEvent event : events) {
            if (executor.isDisabled(event.source())) {
                continue;
            }
            for (TriggerEntry trigger : triggersDao.list(event.source(), event.triggerVersion())) {
                if (DefaultEventFilter.filter(event.conditions(), trigger)) {
                    matches.add(new MatchedEvent(event, trigger));
                }
            }
        }

        List<PartialProcessKey> result = new ArrayList<>();
        for (MatchedEvent match : matches) {
            ExternalEvent event = match.event();
            Event internalEvent = Event.builder()
                    .id(event.id())
                    .name(event.source())
                    .attributes(event.attributes())
                    .initiator(memo(new EventInitiatorSupplier("author", userManager,
                            Collections.singletonMap("author", event.initiatorUsername()))))
                    .build();
            result.addAll(executor.execute(internalEvent, initiatorResolver, Collections.singletonList(match.trigger())));
        }
        return result;
    }

    private record MatchedEvent(ExternalEvent event, TriggerEntry trigger) {
    }
}
