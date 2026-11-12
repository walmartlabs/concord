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

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.org.triggers.TriggerEntry;
import com.walmartlabs.concord.server.org.triggers.TriggersDao;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.events.ExternalEvent;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultExternalEventServiceTest {

    @Mock private TriggersDao triggersDao;
    @Mock private TriggerProcessExecutor executor;
    @Mock private TriggerEventInitiatorResolver initiatorResolver;
    @Mock private UserManager userManager;
    @Mock private AuditLog auditLog;

    @Test
    void submitsOnlyMatchingTriggers() {
        TriggerEntry matching = trigger("pr.*");
        TriggerEntry unmatched = trigger("qa");
        TriggerEntry invalid = trigger("[");
        PartialProcessKey matchingKey = PartialProcessKey.create();
        PartialProcessKey unmatchedKey = PartialProcessKey.create();
        PartialProcessKey invalidKey = PartialProcessKey.create();
        Map<TriggerEntry, PartialProcessKey> keys = Map.of(matching, matchingKey, unmatched, unmatchedKey, invalid, invalidKey);
        when(triggersDao.list("oneops", 2)).thenReturn(List.of(matching, unmatched, invalid));
        when(executor.execute(any(Event.class), eq(initiatorResolver), anyList())).thenAnswer(invocation -> {
            List<TriggerEntry> triggers = invocation.getArgument(2);
            return triggers.stream().map(keys::get).toList();
        });

        List<PartialProcessKey> result = service().submit(List.of(event(2)));

        assertEquals(List.of(matchingKey), result);
        verify(executor).execute(any(Event.class), eq(initiatorResolver), eq(List.of(matching)));
        verify(executor).isDisabled("oneops");
        verifyNoMoreInteractions(executor);
    }

    @Test
    void disabledSourceDoesNotLookUpOrSubmit() {
        when(executor.isDisabled("oneops")).thenReturn(true);

        assertEquals(List.of(), service().submit(List.of(event(2))));

        verify(executor).isDisabled("oneops");
        verifyNoMoreInteractions(executor);
        verifyNoInteractions(triggersDao, initiatorResolver, userManager, auditLog);
    }

    @Test
    void emptyEventsHaveNoEffects() {
        assertEquals(List.of(), service().submit(List.of()));

        verifyNoInteractions(triggersDao, executor, initiatorResolver, userManager, auditLog);
    }

    @Test
    void collectsAllMatchesBeforeSubmitting() {
        RuntimeException failure = new RuntimeException("lookup failed");
        when(triggersDao.list("oneops", 1)).thenReturn(List.of(trigger("pr.*", 1)));
        when(triggersDao.list("oneops", 2)).thenThrow(failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> service().submit(List.of(event(1), event(2)))));
        verify(executor, never()).execute(any(Event.class), any(), anyList());
    }

    private DefaultExternalEventService service() {
        return new DefaultExternalEventService(triggersDao, executor, initiatorResolver, userManager, auditLog);
    }

    private static ExternalEvent event(int version) {
        Map<String, Object> conditions = Map.of("env", "prod", "version", version);
        return new ExternalEvent("oneops", "event-id", version, conditions, conditions, null);
    }

    private static TriggerEntry trigger(String environment) {
        return trigger(environment, 2);
    }

    private static TriggerEntry trigger(String environment, int version) {
        return new TriggerEntry(UUID.randomUUID(), null, null, null, null, null, null,
                "oneops", null, null, Map.of("env", environment, "version", version), Map.of());
    }
}
