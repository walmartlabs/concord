package com.walmartlabs.concord.server.console;

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

import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.jooq.enums.ProcessExecMode;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessCardSecurityTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID cardId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final ProcessCardManager.Dao dao = mock(ProcessCardManager.Dao.class);
    private final ProjectAccessManager access = mock(ProjectAccessManager.class);
    private final ProjectDao projects = mock(ProjectDao.class);
    private final RepositoryDao repositories = mock(RepositoryDao.class);
    private final TeamDao teams = mock(TeamDao.class);
    private final Subject subject = mock(Subject.class);
    private final ProcessCardManager manager = new ProcessCardManager(dao, access, projects, repositories, teams);
    private final ProcessCardEntry card = ProcessCardEntry.builder().id(cardId).name("card").isCustomForm(true).build();

    @BeforeEach
    void bindSubject() {
        var user = mock(UserEntry.class);
        when(user.getId()).thenReturn(userId);
        when(user.getName()).thenReturn("test");
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(new UserPrincipal("test", user), "test"));
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);
        when(dao.getProjectId(cardId)).thenReturn(projectId);
        when(dao.get(cardId)).thenReturn(card);
    }

    @AfterEach
    void unbindSubject() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void creationRequiresProjectWriterEvenForPublicProjects() {
        doThrow(new UnauthorizedException("denied")).when(access).assertAccess(projectId, ResourceAccessLevel.WRITER, true);
        assertThrows(UnauthorizedException.class, () -> create(null, projectId, null));
        verifyNoInteractions(repositories);
        verify(dao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repositoryMustBelongToTargetProject() {
        var repoId = UUID.randomUUID();
        when(repositories.getProjectId(repoId)).thenReturn(UUID.randomUUID());
        assertThrows(ValidationErrorsException.class, () -> create(null, projectId, repoId));
        verify(dao, never()).insert(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cardOwnerAlsoNeedsCurrentProjectWriteAccess() {
        owner();
        doThrow(new UnauthorizedException("revoked")).when(access).assertAccess(projectId, ResourceAccessLevel.WRITER, true);
        assertThrows(UnauthorizedException.class, () -> manager.assertAccess(cardId));
    }

    @Test
    void projectWriterCannotModifySomeoneElsesCard() {
        assertThrows(UnauthorizedException.class, () -> create(cardId, projectId, null));
        verify(dao, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void rebindRequiresOldAndNewProjectAuthorityAndClearsShares() {
        owner();
        var newProject = UUID.randomUUID();
        create(cardId, newProject, null);
        verify(access).assertAccess(newProject, ResourceAccessLevel.WRITER, true);
        verify(access).assertAccess(projectId, ResourceAccessLevel.WRITER, true);
        verify(dao).update(cardId, newProject, null, "card", null, null, null, null, null, null, true);
    }

    @Test
    void rebindCannotKeepForeignRepository() {
        owner();
        var repoId = UUID.randomUUID();
        when(dao.getRepositoryId(cardId)).thenReturn(repoId);
        when(repositories.getProjectId(repoId)).thenReturn(projectId);
        assertThrows(ValidationErrorsException.class, () -> create(cardId, UUID.randomUUID(), null));
        verify(dao, never()).update(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void readAndListRequireGrantAndIndependentProjectReadAccess() {
        when(dao.listCards(userId, false)).thenReturn(List.of(card));
        assertThrows(UnauthorizedException.class, () -> manager.assertReadable(cardId));
        assertTrue(manager.listUserCards(userId).isEmpty());
        when(dao.hasGrant(cardId, userId)).thenReturn(true);
        manager.assertReadable(cardId);
        assertEquals(List.of(card), manager.listUserCards(userId));
        doThrow(new UnauthorizedException("revoked")).when(access).assertAccess(projectId, ResourceAccessLevel.READER, true);
        assertThrows(UnauthorizedException.class, () -> manager.assertReadable(cardId));
        assertTrue(manager.listUserCards(userId).isEmpty());
    }

    @Test
    void adminCanReadWithoutAShare() {
        when(subject.hasRole(Roles.ADMIN)).thenReturn(true);
        manager.assertReadable(cardId);
        verify(access).assertAccess(projectId, ResourceAccessLevel.READER, true);
    }

    @Test
    void invalidRecipientsAreRejectedBeforeAnyMutation() {
        owner();
        var project = mock(ProjectEntry.class);
        when(projects.get(projectId)).thenReturn(project);
        var recipient = UUID.randomUUID();
        assertThrows(ValidationErrorsException.class, () -> manager.updateAccess(cardId, List.of(), List.of(recipient)));
        verify(dao, never()).tx(any());
    }

    @Test
    void teamsMustBelongToOrganizationAndHaveProjectPermission() {
        owner();
        var project = mock(ProjectEntry.class);
        when(project.getOrgId()).thenReturn(orgId);
        when(project.getId()).thenReturn(projectId);
        when(projects.get(projectId)).thenReturn(project);
        var teamId = UUID.randomUUID();
        when(teams.getOrgId(teamId)).thenReturn(UUID.randomUUID());
        assertThrows(ValidationErrorsException.class, () -> manager.updateAccess(cardId, List.of(teamId), List.of()));
        when(teams.getOrgId(teamId)).thenReturn(orgId);
        when(projects.getAccessLevel(projectId)).thenReturn(List.of());
        assertThrows(ValidationErrorsException.class, () -> manager.updateAccess(cardId, List.of(teamId), List.of()));
        verify(dao, never()).tx(any());
        when(projects.getAccessLevel(projectId)).thenReturn(List.of(new ResourceAccessEntry(teamId, null, null, ResourceAccessLevel.READER)));
        manager.updateAccess(cardId, List.of(teamId), List.of());
        verify(dao).tx(any());
    }

    @Test
    void metadataHtmlAndDataAllUseTheSameReadGate() {
        var cards = mock(ProcessCardManager.class);
        when(cards.get(cardId)).thenReturn(card);
        doThrow(new UnauthorizedException("denied")).when(cards).assertReadable(cardId);
        var resource = resource(cards);
        assertThrows(UnauthorizedException.class, () -> resource.get(cardId));
        assertThrows(UnauthorizedException.class, () -> resource.getForm(cardId, null));
        assertThrows(UnauthorizedException.class, () -> resource.getFormData(cardId));
        assertThrows(UnauthorizedException.class, () -> resource.getData(cardId));
        assertThrows(UnauthorizedException.class, () -> resource.start(cardId, Map.of(), null));
        verify(cards, never()).getForm(any(), any());
        verify(cards, never()).getFormData(any());
    }

    @Test
    void directHtmlResponsesAreOpaqueSandboxedAndUncacheable() {
        var html = "<script>document.cookie</script>".getBytes(StandardCharsets.UTF_8);
        try (var response = ProcessCardResource.formResponse(html, true)) {
            assertArrayEquals(html, (byte[]) response.getEntity());
            var csp = response.getHeaderString("Content-Security-Policy");
            assertTrue(csp.contains("sandbox allow-scripts;"));
            assertFalse(csp.contains("allow-same-origin"));
            for (var directive : List.of("default-src 'none'", "connect-src 'none'", "form-action 'none'",
                    "base-uri 'none'", "object-src 'none'", "frame-src 'none'")) {
                assertTrue(csp.contains(directive), csp);
            }
            assertEquals("no-store", response.getHeaderString("Cache-Control"));
            assertEquals("no-referrer", response.getHeaderString("Referrer-Policy"));
            assertEquals("off", response.getHeaderString("X-DNS-Prefetch-Control"));
        }
        try (var response = ProcessCardResource.formResponse(html, false)) {
            assertEquals("text/plain", response.getMediaType().toString());
            assertFalse(response.getHeaderString("Content-Security-Policy").contains("allow-scripts"));
            assertArrayEquals(html, (byte[]) response.getEntity());
        }
    }

    @Test
    void onlySameOriginIframeRequestsReceiveActiveHtml() {
        var cards = mock(ProcessCardManager.class);
        when(cards.get(cardId)).thenReturn(card);
        when(cards.getForm(eq(cardId), any())).thenReturn(Optional.of(java.nio.file.Path.of("unused-form.html")));
        var resource = resource(cards);
        var request = mock(HttpServletRequest.class);
        try (var response = resource.getForm(cardId, request)) {
            assertEquals("text/plain", response.getMediaType().toString());
        }
        when(request.getHeader("Sec-Fetch-Dest")).thenReturn("iframe");
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("cross-site");
        try (var response = resource.getForm(cardId, request)) {
            assertEquals("text/plain", response.getMediaType().toString());
        }
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-origin");
        try (var response = resource.getForm(cardId, request)) {
            assertEquals("text/html", response.getMediaType().toString());
        }
    }

    @Test
    void submissionIsBoundedJsonArgumentsOnly() {
        var resource = resource(manager);
        resource.validateArguments(Map.of("name", "test", "nested", List.of(1, true, Map.of("value", "x"))));
        assertThrows(ValidationErrorsException.class, () -> resource.validateArguments(null));
        assertThrows(ValidationErrorsException.class, () -> resource.validateArguments(Map.of("value", "x".repeat(65536))));
        assertThrows(ValidationErrorsException.class, () -> resource.validateArguments(Map.of("value", new Object())));
        assertThrows(ValidationErrorsException.class, () -> resource.validateArguments(Map.of("__proto__", Map.of())));
        Map<String, Object> deep = Map.of();
        for (var i = 0; i < 18; i++) {
            deep = Map.of("nested", deep);
        }
        var tooDeep = deep;
        assertThrows(ValidationErrorsException.class, () -> resource.validateArguments(tooDeep));
    }

    @Test
    void startUsesOnlyStoredTargetAndNormalProcessManager() throws Exception {
        var cards = mock(ProcessCardManager.class);
        when(cards.get(cardId)).thenReturn(card);
        var repoId = UUID.randomUUID();
        when(cards.getTarget(cardId)).thenReturn(new ProcessCardManager.Target(projectId, repoId, "storedFlow"));
        when(repositories.getProjectId(repoId)).thenReturn(projectId);
        var project = mock(ProjectEntry.class);
        when(project.getProcessExecMode()).thenReturn(ProcessExecMode.READERS);
        when(access.assertAccess(projectId, ResourceAccessLevel.READER, true)).thenReturn(project);
        when(projects.getOrgId(projectId)).thenReturn(orgId);
        var processes = mock(ProcessManager.class);
        var instanceId = UUID.randomUUID();
        when(processes.start(any())).thenReturn(new ProcessManager.ProcessResult(instanceId));
        var args = Map.<String, Object>of("org", "foreign", "project", "foreign", "repo", "foreign",
                "entryPoint", "foreign", "name", "test");
        var resource = new ProcessCardResource(null, cards, projects, repositories,
                new ConcordObjectMapper(TestObjectMapper.INSTANCE), access, processes);
        assertEquals(Map.of("instanceId", instanceId), resource.start(cardId, args, null));
        var capture = ArgumentCaptor.forClass(Payload.class);
        verify(processes).start(capture.capture());
        var payload = capture.getValue();
        try {
            verify(cards).assertReadable(cardId);
            verify(access).assertAccess(projectId, ResourceAccessLevel.READER, true);
            assertEquals(orgId, payload.getHeader(Payload.ORGANIZATION_ID));
            assertEquals(projectId, payload.getHeader(Payload.PROJECT_ID));
            assertEquals(repoId, payload.getHeader(Payload.REPOSITORY_ID));
            assertEquals("storedFlow", payload.getHeader(Payload.ENTRY_POINT));
            assertEquals(Map.of("arguments", args), payload.getHeader(Payload.CONFIGURATION));
        } finally {
            PathUtils.deleteRecursively(payload.getHeader(Payload.BASE_DIR));
        }
    }

    private ProcessCardResource resource(ProcessCardManager cards) {
        return new ProcessCardResource(null, cards, projects, repositories,
                new ConcordObjectMapper(TestObjectMapper.INSTANCE), access, mock(ProcessManager.class));
    }

    private void owner() {
        var owner = mock(EntityOwner.class);
        when(owner.id()).thenReturn(userId);
        when(dao.getOwner(cardId)).thenReturn(owner);
    }

    private void create(UUID id, UUID targetProject, UUID repoId) {
        manager.createOrUpdate(id, targetProject, repoId, "card", Optional.empty(), null, null, null, null, null);
    }
}
