package com.walmartlabs.concord.server.org.triggers;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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
import com.walmartlabs.concord.server.cfg.TriggersConfiguration;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;

import static java.time.temporal.ChronoUnit.MICROS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class TriggerSchedulerRunAsTest {

    private static final UUID CRON_ID = UUID.fromString("1f9ae527-e7ab-42c0-b0e5-0092f9285f22");
    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID REPOSITORY_ID = UUID.randomUUID();
    private static final String SECRET_NAME = "cron-api-key";

    private TriggerScheduleDao scheduleDao;
    private ProcessManager processManager;
    private UserSecurityContext securityContext;
    private SecretManager secretManager;
    private UserManager userManager;
    private AuditLog auditLog;
    private AuditLog.EntryBuilder auditEntry;
    private TriggerScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduleDao = mock(TriggerScheduleDao.class);
        processManager = mock(ProcessManager.class);
        securityContext = mock(UserSecurityContext.class);
        secretManager = mock(SecretManager.class);
        userManager = mock(UserManager.class);
        auditLog = mock(AuditLog.class);
        auditEntry = mock(AuditLog.EntryBuilder.class, RETURNS_SELF);
        when(auditLog.add(AuditObject.PROCESS, AuditAction.CREATE)).thenReturn(auditEntry);
        when(securityContext.runAs(any(), any()))
                .thenAnswer(invocation -> invocation.<Callable<?>>getArgument(1).call());
        var repositoryDao = mock(RepositoryDao.class);
        var repository = mock(RepositoryEntry.class);
        when(repositoryDao.get(REPOSITORY_ID)).thenReturn(repository);
        var cfg = mock(TriggersConfiguration.class);
        when(cfg.getDisabled()).thenReturn(List.of());
        scheduler = new TriggerScheduler(scheduleDao, repositoryDao, processManager, securityContext, cfg,
                secretManager, userManager, auditLog, mock(PolicyManager.class));
    }

    @Test
    void forbiddenRunAsIsResolvedForTriggerProjectAndAuditedWithoutStartingProcess() throws Exception {
        schedule(true);
        when(secretManager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null))
                .thenThrow(new UnauthorizedException("Secret is not scoped to the trigger project"));

        scheduler.performTask();

        verify(secretManager).assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null);
        verify(securityContext, times(2)).runAs(eq(CRON_ID), any());
        verifyNoInteractions(processManager, userManager);
        verify(auditLog).add(AuditObject.PROCESS, AuditAction.CREATE);
        verify(auditEntry).field("projectId", PROJECT_ID);
        verify(auditEntry).field("status", "FAILED");
        verify(auditEntry).field("reason", "Secret is not scoped to the trigger project");
        verify(auditEntry).log();
    }

    @Test
    void validRunAsStartsProcessAsApiKeyOwner() throws Exception {
        schedule(true);
        var ownerId = UUID.randomUUID();
        var key = mock(ApiKeyEntry.class);
        when(key.getUserId()).thenReturn(ownerId);
        when(secretManager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null)).thenReturn(key);
        var owner = new UserEntry(ownerId, "api-key-owner", null, null, null, null, null, null, false, null, false);
        when(userManager.get(ownerId)).thenReturn(Optional.of(owner));

        scheduler.performTask();

        verify(secretManager).assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null);
        verify(securityContext).runAs(eq(CRON_ID), any());
        verify(securityContext).runAs(eq(ownerId), any());
        var payload = ArgumentCaptor.forClass(Payload.class);
        verify(processManager).start(payload.capture());
        assertEquals(ownerId, payload.getValue().getHeader(Payload.INITIATOR_ID));
        assertEquals("api-key-owner", payload.getValue().getHeader(Payload.INITIATOR));
        assertEquals(PROJECT_ID, payload.getValue().getHeader(Payload.PROJECT_ID));
        verifyNoInteractions(auditLog);
    }

    @Test
    void triggerWithoutRunAsStillStartsAsCron() throws Exception {
        schedule(false);

        scheduler.performTask();

        verifyNoInteractions(secretManager, userManager, auditLog);
        verify(securityContext).runAs(eq(CRON_ID), any());
        var payload = ArgumentCaptor.forClass(Payload.class);
        verify(processManager).start(payload.capture());
        assertEquals(CRON_ID, payload.getValue().getHeader(Payload.INITIATOR_ID));
        assertEquals("cron", payload.getValue().getHeader(Payload.INITIATOR));
    }

    private void schedule(boolean runAs) {
        var cfg = runAs ? Map.of("entryPoint", "main", "runAs", Map.of("withSecret", SECRET_NAME))
                : Map.<String, Object>of("entryPoint", "main");
        var trigger = new TriggerEntry(UUID.randomUUID(), ORG_ID, "org", PROJECT_ID, "project",
                REPOSITORY_ID, "repository", "cron", List.of(), Map.of(), Map.of("spec", "* * * * *"), cfg);
        var fireAt = OffsetDateTime.now().plusMinutes(1).truncatedTo(MICROS);
        when(scheduleDao.findNext()).thenReturn(TriggerSchedulerEntry.builder()
                .trigger(trigger).fireAt(fireAt).nextExecutionAt(fireAt.plusMinutes(1)).build())
                .thenReturn(null);
    }
}
