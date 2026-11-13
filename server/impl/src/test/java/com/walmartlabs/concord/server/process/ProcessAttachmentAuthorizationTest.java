package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ZipService;
import com.walmartlabs.concord.common.cfg.UnzipLimits;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.waits.ProcessWaitManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessAttachmentAuthorizationTest {

    private final UUID instanceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final OffsetDateTime createdAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final ProcessEntry entry = mock(ProcessEntry.class);
    private final ProcessQueueManager queueManager = mock(ProcessQueueManager.class);
    private final ProcessQueueDao queueDao = mock(ProcessQueueDao.class);
    private final ProjectAccessManager projectAccessManager = mock(ProjectAccessManager.class);
    private final ProcessStateManager stateManager = mock(ProcessStateManager.class);
    private final PolicyManager policyManager = mock(PolicyManager.class);
    private final ZipService zipService = spy(new ZipService(UnzipLimits.getInstance()));
    private final Subject subject = mock(Subject.class);
    private final SimplePrincipalCollection principals = new SimplePrincipalCollection();
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        var user = new UserEntry(userId, "uploader", null, null, null, null, null, null, false, null, false);
        principals.add(new UserPrincipal("test", user), "test");
        when(subject.getPrincipals()).thenReturn(principals);
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);

        when(entry.instanceId()).thenReturn(instanceId);
        when(entry.createdAt()).thenReturn(createdAt);
        when(entry.initiatorId()).thenReturn(UUID.randomUUID());
        when(queueManager.get(PartialProcessKey.from(instanceId))).thenReturn(entry);

        resource = new ProcessResource(mock(ProcessWaitManager.class), mock(ProcessManager.class),
                queueDao, queueManager, mock(PayloadManager.class), stateManager,
                mock(SecretStoreConfiguration.class), mock(EncryptedProjectValueManager.class),
                projectAccessManager, new ProcessAccessManager(projectAccessManager, queueManager),
                mock(ProcessKeyCache.class), new ObjectMapper(),
                mock(ProcessConfiguration.class), mock(ProcessLogManager.class),
                mock(ProcessLogAccessManager.class), mock(ProcessLogManager.class), policyManager,
                mock(UuidGenerator.class), zipService, mock(ProcessResourceV2.class));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void rejectsCrossTenantUploadBeforeReadingOrChangingAnything() {
        when(entry.projectId()).thenReturn(projectId);
        doThrow(new UnauthorizedException("different tenant"))
                .when(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);

        assertUploadDenied();

        verify(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);
    }

    @Test
    void projectReaderMustPassOwnerCheck() {
        when(entry.projectId()).thenReturn(projectId);
        doThrow(new UnauthorizedException("reader is not an owner"))
                .when(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);

        assertUploadDenied();

        verify(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);
    }

    @Test
    void globalReaderCannotUploadToUnrelatedProjectlessProcess() {
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(true);

        assertUploadDenied();

        verifyNoInteractions(projectAccessManager);
    }

    @Test
    void globalReaderStillRequiresProjectOwnership() {
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(true);
        when(entry.projectId()).thenReturn(projectId);
        doThrow(new UnauthorizedException("not an owner"))
                .when(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);

        assertUploadDenied();

        verify(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);
    }

    @Test
    void sessionKeyForAnotherProcessCannotUpload() {
        principals.add(new SessionKeyPrincipal(PartialProcessKey.from(UUID.randomUUID())), "test");

        assertUploadDenied();
    }

    @Test
    void administratorCanUploadArchiveAndOutVariables() throws Exception {
        when(subject.hasRole(Roles.ADMIN)).thenReturn(true);

        assertUploadSucceeds();

        verifyNoInteractions(projectAccessManager);
    }

    @Test
    void systemWriterCanUploadArchiveAndOutVariables() throws Exception {
        when(subject.hasRole(Roles.SYSTEM_WRITER)).thenReturn(true);

        assertUploadSucceeds();

        verifyNoInteractions(projectAccessManager);
    }

    @Test
    void initiatorCanUploadArchiveAndOutVariables() throws Exception {
        when(entry.initiatorId()).thenReturn(userId);

        assertUploadSucceeds();

        verifyNoInteractions(projectAccessManager);
    }

    @Test
    void projectOwnerCanUploadArchiveAndOutVariables() throws Exception {
        when(entry.projectId()).thenReturn(projectId);

        assertUploadSucceeds();

        verify(projectAccessManager).assertAccess(projectId, ResourceAccessLevel.OWNER, true);
    }

    @Test
    void sessionKeyScopedToProcessCanUploadArchiveAndOutVariables() throws Exception {
        principals.add(new SessionKeyPrincipal(PartialProcessKey.from(instanceId)), "test");

        assertUploadSucceeds();

        verifyNoInteractions(projectAccessManager);
    }

    private void assertUploadDenied() {
        var data = mock(InputStream.class);

        assertThrows(UnauthorizedException.class, () -> resource.uploadAttachments(instanceId, data));

        verifyNoInteractions(data, zipService, stateManager, queueDao, policyManager);
    }

    private void assertUploadSucceeds() throws Exception {
        var tx = mock(DSLContext.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, AbstractDao.Tx.class).run(tx);
            return null;
        }).when(stateManager).tx(any(AbstractDao.Tx.class));
        var processKey = new ProcessKey(instanceId, createdAt);

        resource.uploadAttachments(instanceId, new ByteArrayInputStream(archive()));

        verify(zipService).unzip(any(Path.class), any(Path.class));
        verify(stateManager).deleteDirectory(tx, processKey, "_attachments/_state");
        verify(stateManager).importPath(eq(tx), eq(processKey), eq("_attachments"), any(Path.class), any());
        verify(queueDao).updateMeta(processKey, Map.of("out", Map.of("result", "authorized")));
        verify(queueDao, never()).removeMeta(any(), anyString());
    }

    private byte[] archive() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("out.json"));
            zip.write("{\"result\":\"authorized\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
