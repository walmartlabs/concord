package com.walmartlabs.concord.server.process.form;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.process.ProcessAccessManager;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserManager;
import jakarta.ws.rs.core.Response;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FormResourceAuthorizationTest {

    private final UUID instanceId = UUID.randomUUID();
    private final ProcessStateManager stateManager = mock(ProcessStateManager.class);
    private final ProcessAccessManager accessManager = mock(ProcessAccessManager.class);
    private final UserManager userManager = mock(UserManager.class);
    private final FormAccessManager formAccessManager = new FormAccessManager(stateManager, userManager);
    private final FormResourceV1 v1 = mock(FormResourceV1.class);
    private final FormResourceV2 v2 = mock(FormResourceV2.class);
    private final FormResource resource = new FormResource(stateManager, accessManager, formAccessManager, v1, v2);
    private final String formsPath = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
            Constants.Files.JOB_STATE_DIR_NAME, Constants.Files.JOB_FORMS_V2_DIR_NAME);

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void deniedListInspectsDelegationButPreservesOriginalDenial() {
        var denied = denyProcessAccess();

        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.list(instanceId)));

        verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        verify(v1).list(instanceId);
        verifyNoInteractions(v2, userManager);
    }

    @Test
    void deniedGetInspectsDelegationButDoesNotReadFormFields() {
        var denied = denyProcessAccess();

        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval")));

        verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        verify(v1).list(instanceId);
        verify(v1, never()).get(any(), any());
        verifyNoInteractions(v2, userManager);
    }

    @Test
    void authorizedListDispatchesToV1AfterAccessCheck() {
        var expected = List.of(mock(FormListEntry.class));
        when(v1.list(instanceId)).thenReturn(expected);

        assertSame(expected, resource.list(instanceId));

        var order = inOrder(accessManager, stateManager, v1);
        order.verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        order.verify(stateManager).exists(PartialProcessKey.from(instanceId), formsPath);
        order.verify(v1).list(instanceId);
        verifyNoInteractions(v2);
    }

    @Test
    void authorizedListDispatchesToV2AfterAccessCheck() {
        when(stateManager.exists(PartialProcessKey.from(instanceId), formsPath)).thenReturn(true);
        var expected = List.of(mock(FormListEntry.class));
        when(v2.list(instanceId)).thenReturn(expected);

        assertSame(expected, resource.list(instanceId));

        var order = inOrder(accessManager, stateManager, v2);
        order.verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        order.verify(stateManager).exists(PartialProcessKey.from(instanceId), formsPath);
        order.verify(v2).list(instanceId);
        verifyNoInteractions(v1);
    }

    @Test
    void authorizedGetDispatchesToV1AfterAccessCheck() {
        var expected = mock(FormInstanceEntry.class);
        when(v1.get(instanceId, "approval")).thenReturn(expected);

        assertSame(expected, resource.get(instanceId, "approval"));

        var order = inOrder(accessManager, stateManager, v1);
        order.verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        order.verify(stateManager).exists(PartialProcessKey.from(instanceId), formsPath);
        order.verify(v1).get(instanceId, "approval");
        verifyNoInteractions(v2);
    }

    @Test
    void authorizedGetDispatchesToV2AfterAccessCheck() {
        when(stateManager.exists(PartialProcessKey.from(instanceId), formsPath)).thenReturn(true);
        var expected = mock(FormInstanceEntry.class);
        when(v2.get(instanceId, "approval")).thenReturn(expected);

        assertSame(expected, resource.get(instanceId, "approval"));

        var order = inOrder(accessManager, stateManager, v2);
        order.verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        order.verify(stateManager).exists(PartialProcessKey.from(instanceId), formsPath);
        order.verify(v2).get(instanceId, "approval");
        verifyNoInteractions(v1);
    }

    @Test
    void formSpecificDenialIsPreservedAfterProcessAccess() {
        var denied = new UnauthorizedException("form-specific access denied");
        when(v1.get(instanceId, "approval")).thenThrow(denied);

        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval")));

        verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        verifyNoInteractions(v2);
    }

    @TestFactory
    Stream<DynamicTest> realProcessAccessManagerRejectsCrossTenantUnrestrictedForms() {
        return cases(Stream.of(false, true),
                FormResourceAuthorizationTest::realProcessAccessManagerRejectsCrossTenantUnrestrictedForms);
    }

    void realProcessAccessManagerRejectsCrossTenantUnrestrictedForms(boolean runtimeV2) {
        var userId = UUID.randomUUID();
        var user = new UserEntry(userId, "outsider", null, null, null, null, null, null, false, null, false);
        var subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(new UserPrincipal("test", user), "test"));
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);

        var entry = mock(ProcessEntry.class);
        var orgId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        when(entry.initiatorId()).thenReturn(UUID.randomUUID());
        when(entry.orgId()).thenReturn(orgId);
        when(entry.projectId()).thenReturn(projectId);
        var queueManager = mock(ProcessQueueManager.class);
        when(queueManager.get(PartialProcessKey.from(instanceId), Collections.emptySet())).thenReturn(entry);
        var projectAccessManager = mock(ProjectAccessManager.class);
        doThrow(new UnauthorizedException("different tenant")).when(projectAccessManager)
                .assertAccess(orgId, projectId, null, ResourceAccessLevel.READER, false);
        var realAccessManager = new ProcessAccessManager(projectAccessManager, queueManager);
        var securedResource = new FormResource(stateManager, realAccessManager, formAccessManager, v1, v2);
        forms(runtimeV2, List.of(form("approval", null)));

        assertThrows(UnauthorizedException.class, () -> securedResource.list(instanceId));
        assertThrows(UnauthorizedException.class, () -> securedResource.get(instanceId, "approval"));

        verify(projectAccessManager, times(2))
                .assertAccess(orgId, projectId, null, ResourceAccessLevel.READER, false);
        verify(v1, never()).get(any(), any());
        verify(v2, never()).get(any(), any());
        verifyNoInteractions(userManager);
    }

    @TestFactory
    Stream<DynamicTest> nonDelegatingFormsDoNotGrantReadAccess() {
        return cases(nonDelegatingOptions(), (fixture, c) ->
                fixture.nonDelegatingFormsDoNotGrantReadAccess(c.runtimeV2(), c.runAs()));
    }

    void nonDelegatingFormsDoNotGrantReadAccess(boolean runtimeV2, Map<String, Object> runAs) {
        var denied = denyProcessAccess();
        forms(runtimeV2, List.of(form("approval", runAs)));

        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.list(instanceId)));
        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval")));

        verify(v1, never()).get(any(), any());
        verify(v2, never()).get(any(), any());
        verifyNoInteractions(userManager);
    }

    static Stream<DelegationCase> nonDelegatingOptions() {
        return Stream.of(false, true).flatMap(runtimeV2 -> Stream.<Map<String, Object>>of(
                        null, Map.of(), Map.of("keep", true), Map.of("username", List.of()),
                        Map.of("ldap", Map.of("group", List.of())), Map.of("users", List.of("delegate")))
                .map(options -> new DelegationCase(runtimeV2, options, false)));
    }

    @TestFactory
    Stream<DynamicTest> explicitDelegatesCanReadNamedForm() {
        return cases(delegatingOptions(), (fixture, c) ->
                fixture.explicitDelegatesCanReadNamedForm(c.runtimeV2(), c.runAs()));
    }

    void explicitDelegatesCanReadNamedForm(boolean runtimeV2, Map<String, Object> runAs) {
        bindUser("delegate");
        denyProcessAccess();
        when(userManager.getCurrentUserInfo()).thenReturn(UserInfoProvider.UserInfo.builder()
                .groups(Set.of("CN=Approvers,OU=Groups,DC=example")).build());
        var approval = form("approval", runAs);
        forms(runtimeV2, List.of(approval));
        var expected = mock(FormInstanceEntry.class);
        when(v1.get(instanceId, "approval")).thenReturn(expected);
        when(v2.get(instanceId, "approval")).thenReturn(expected);

        assertSame(expected, resource.get(instanceId, "approval"));
        assertEquals(List.of(approval), resource.list(instanceId));
        if (runtimeV2) {
            verify(v2).get(instanceId, "approval");
            verifyNoInteractions(v1);
        } else {
            verify(v1).get(instanceId, "approval");
            verifyNoInteractions(v2);
        }
    }

    static Stream<DelegationCase> delegatingOptions() {
        return Stream.of(false, true).flatMap(runtimeV2 -> Stream.<Map<String, Object>>of(
                        Map.of("username", "delegate"),
                        Map.of("username", List.of("other", "delegate")),
                        Map.of("ldap", Map.of("group", "approvers")),
                        Map.of("ldap", Map.of("group", "CN=Approvers,.*")),
                        Map.of("ldap", List.of(Map.of("group", "Approvers"))),
                        Map.of("ldap", Map.of("group", List.of("Other", "Approvers"))),
                        Map.of("username", "delegate", "ldap", Map.of("group", "Approvers")))
                .map(options -> new DelegationCase(runtimeV2, options, false)));
    }

    @TestFactory
    Stream<DynamicTest> listOnlyIncludesMatchingExplicitDelegations() {
        return cases(Stream.of(false, true), FormResourceAuthorizationTest::listOnlyIncludesMatchingExplicitDelegations);
    }

    void listOnlyIncludesMatchingExplicitDelegations(boolean runtimeV2) {
        bindUser("delegate");
        denyProcessAccess();
        when(userManager.getCurrentUserInfo()).thenReturn(UserInfoProvider.UserInfo.builder()
                .groups(Set.of("Approvers")).build());
        var userForm = form("user", Map.of("username", "delegate"));
        var groupForm = form("group", Map.of("ldap", Map.of("group", "Approvers")));
        forms(runtimeV2, List.of(form("public", null), form("empty", Map.of()), form("keep", Map.of("keep", true)),
                form("otherUser", Map.of("username", "other")),
                form("otherGroup", Map.of("ldap", Map.of("group", "Other"))),
                form("combinedMismatch", Map.of("username", "delegate", "ldap", Map.of("group", "Other"))),
                userForm, groupForm));

        assertEquals(List.of(userForm, groupForm), resource.list(instanceId));
        assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "public"));
        assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "missing"));
        verify(v1, never()).get(any(), any());
        verify(v2, never()).get(any(), any());
    }

    @TestFactory
    Stream<DynamicTest> explicitMismatchesDenyAndPreserveFormSpecificErrors() {
        return cases(mismatchedOptions(), (fixture, c) ->
                fixture.explicitMismatchesDenyAndPreserveFormSpecificErrors(c.runtimeV2(), c.runAs(), c.ldapDenial()));
    }

    void explicitMismatchesDenyAndPreserveFormSpecificErrors(boolean runtimeV2, Map<String, Object> runAs,
                                                           boolean ldapDenial) {
        bindUser("delegate");
        var denied = denyProcessAccess();
        when(userManager.getCurrentUserInfo()).thenReturn(UserInfoProvider.UserInfo.builder()
                .groups(Set.of("Other")).build());
        forms(runtimeV2, List.of(form("approval", runAs)));

        var error = assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval"));
        assertTrue(error.getMessage().contains(ldapDenial
                ? "Expected LDAP group(s) '[Approvers]'"
                : "the necessary permissions to access the form."));
        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.list(instanceId)));
        verify(v1, never()).get(any(), any());
        verify(v2, never()).get(any(), any());
    }

    static Stream<DelegationCase> mismatchedOptions() {
        return Stream.of(false, true).flatMap(runtimeV2 -> Stream.of(
                new DelegationCase(runtimeV2, Map.of("username", "other"), false),
                new DelegationCase(runtimeV2, Map.of("ldap", Map.of("group", "Approvers")), true),
                new DelegationCase(runtimeV2, Map.of("username", "delegate", "ldap", Map.of("group", "Approvers")), true),
                new DelegationCase(runtimeV2, Map.of("username", "other", "ldap", Map.of("group", "Other")), false)));
    }

    @Test
    void missingLdapUserInfoDoesNotGrantDelegation() {
        bindUser("delegate");
        denyProcessAccess();
        forms(false, List.of(form("approval", Map.of("ldap", Map.of("group", "Approvers")))));

        var denied = assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval"));
        assertTrue(denied.getMessage().contains("Expected LDAP group(s) '[Approvers]'"));
    }

    @Test
    void notFoundDoesNotFallBackToDelegationInspection() {
        var notFound = new ConcordApplicationException("not found", Response.Status.NOT_FOUND);
        doThrow(notFound).when(accessManager).assertAccess(instanceId, Collections.emptySet());

        assertSame(notFound, assertThrows(ConcordApplicationException.class, () -> resource.list(instanceId)));
        assertSame(notFound, assertThrows(ConcordApplicationException.class, () -> resource.get(instanceId, "approval")));
        verifyNoInteractions(stateManager, v1, v2, userManager);
    }

    @Test
    void invalidDelegationMetadataDoesNotBecomeAnAuthorizationSuccessOrSilentOmission() {
        denyProcessAccess();
        forms(false, List.of(form("approval", Map.of("username", 123))));

        assertThrows(RuntimeException.class, () -> resource.list(instanceId));
        assertThrows(RuntimeException.class, () -> resource.get(instanceId, "approval"));
        verify(v1, never()).get(any(), any());
    }

    @TestFactory
    Stream<DynamicTest> delegatedReadsStillEnforceRuntimeFormAccess() {
        return cases(Stream.of(false, true), FormResourceAuthorizationTest::delegatedReadsStillEnforceRuntimeFormAccess);
    }

    void delegatedReadsStillEnforceRuntimeFormAccess(boolean runtimeV2) {
        bindUser("delegate");
        denyProcessAccess();
        forms(runtimeV2, List.of(form("approval", Map.of("username", "delegate"))));
        var denied = new UnauthorizedException("runtime form-specific denial");
        when(v1.get(instanceId, "approval")).thenThrow(denied);
        when(v2.get(instanceId, "approval")).thenThrow(denied);

        assertSame(denied, assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval")));
    }

    @Test
    void realProcessInitiatorRetainsUnrestrictedReadAccess() {
        var userId = bindUser("initiator");
        var entry = mock(ProcessEntry.class);
        when(entry.initiatorId()).thenReturn(userId);
        var queueManager = mock(ProcessQueueManager.class);
        when(queueManager.get(PartialProcessKey.from(instanceId), Collections.emptySet())).thenReturn(entry);
        var projectAccessManager = mock(ProjectAccessManager.class);
        var securedResource = new FormResource(stateManager,
                new ProcessAccessManager(projectAccessManager, queueManager), formAccessManager, v1, v2);
        var forms = List.of(form("approval", null));
        when(v1.list(instanceId)).thenReturn(forms);
        var expected = mock(FormInstanceEntry.class);
        when(v1.get(instanceId, "approval")).thenReturn(expected);

        assertSame(forms, securedResource.list(instanceId));
        assertSame(expected, securedResource.get(instanceId, "approval"));
        verifyNoInteractions(projectAccessManager, userManager);
    }

    @TestFactory
    Stream<DynamicTest> realProjectReaderRetainsUnrestrictedReadAccess() {
        return cases(Stream.of(false, true), FormResourceAuthorizationTest::realProjectReaderRetainsUnrestrictedReadAccess);
    }

    void realProjectReaderRetainsUnrestrictedReadAccess(boolean runtimeV2) {
        bindUser("reader");
        var entry = mock(ProcessEntry.class);
        var orgId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        when(entry.initiatorId()).thenReturn(UUID.randomUUID());
        when(entry.orgId()).thenReturn(orgId);
        when(entry.projectId()).thenReturn(projectId);
        var queueManager = mock(ProcessQueueManager.class);
        when(queueManager.get(PartialProcessKey.from(instanceId), Collections.emptySet())).thenReturn(entry);
        var projectAccessManager = mock(ProjectAccessManager.class);
        var securedResource = new FormResource(stateManager,
                new ProcessAccessManager(projectAccessManager, queueManager), formAccessManager, v1, v2);
        var forms = List.of(form("approval", null), form("restricted", Map.of("username", "other")));
        forms(runtimeV2, forms);
        var expected = mock(FormInstanceEntry.class);
        when(v1.get(instanceId, "approval")).thenReturn(expected);
        when(v2.get(instanceId, "approval")).thenReturn(expected);

        assertSame(forms, securedResource.list(instanceId));
        assertSame(expected, securedResource.get(instanceId, "approval"));
        verify(projectAccessManager, times(2))
                .assertAccess(orgId, projectId, null, ResourceAccessLevel.READER, false);
        verifyNoInteractions(userManager);
    }

    @TestFactory
    Stream<DynamicTest> realProjectlessNonReaderCanReadOnlyExplicitDelegations() {
        return cases(Stream.of(false, true),
                FormResourceAuthorizationTest::realProjectlessNonReaderCanReadOnlyExplicitDelegations);
    }

    void realProjectlessNonReaderCanReadOnlyExplicitDelegations(boolean runtimeV2) {
        bindUser("delegate");
        var entry = mock(ProcessEntry.class);
        when(entry.initiatorId()).thenReturn(UUID.randomUUID());
        when(entry.instanceId()).thenReturn(instanceId);
        var queueManager = mock(ProcessQueueManager.class);
        when(queueManager.get(PartialProcessKey.from(instanceId), Collections.emptySet())).thenReturn(entry);
        var projectAccessManager = mock(ProjectAccessManager.class);
        var securedResource = new FormResource(stateManager,
                new ProcessAccessManager(projectAccessManager, queueManager), formAccessManager, v1, v2);
        var delegated = form("approval", Map.of("username", "delegate"));
        forms(runtimeV2, List.of(form("public", null), delegated));
        var expected = mock(FormInstanceEntry.class);
        when(v1.get(instanceId, "approval")).thenReturn(expected);
        when(v2.get(instanceId, "approval")).thenReturn(expected);

        assertEquals(List.of(delegated), securedResource.list(instanceId));
        assertSame(expected, securedResource.get(instanceId, "approval"));
        assertThrows(UnauthorizedException.class, () -> securedResource.get(instanceId, "public"));
        verify(v1, never()).get(instanceId, "public");
        verify(v2, never()).get(instanceId, "public");
        verifyNoInteractions(projectAccessManager);
    }

    private UnauthorizedException denyProcessAccess() {
        var denied = new UnauthorizedException("no process access");
        doThrow(denied).when(accessManager).assertAccess(instanceId, Collections.emptySet());
        return denied;
    }

    private void forms(boolean runtimeV2, List<FormListEntry> forms) {
        when(stateManager.exists(PartialProcessKey.from(instanceId), formsPath)).thenReturn(runtimeV2);
        if (runtimeV2) {
            when(v2.list(instanceId)).thenReturn(forms);
        } else {
            when(v1.list(instanceId)).thenReturn(forms);
        }
    }

    private static FormListEntry form(String name, Map<String, Object> runAs) {
        return new FormListEntry(name, false, false, runAs);
    }

    private static UUID bindUser(String username) {
        var userId = UUID.randomUUID();
        var user = new UserEntry(userId, username, null, null, null, null, null, null, false, null, false);
        var subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(new UserPrincipal("test", user), "test"));
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);
        return userId;
    }

    private record DelegationCase(boolean runtimeV2, Map<String, Object> runAs, boolean ldapDenial) {
    }

    private static <T> Stream<DynamicTest> cases(Stream<T> inputs, BiConsumer<FormResourceAuthorizationTest, T> test) {
        return inputs.map(input -> DynamicTest.dynamicTest(String.valueOf(input), () -> {
            var fixture = new FormResourceAuthorizationTest();
            try {
                test.accept(fixture, input);
            } finally {
                fixture.tearDown();
            }
        }));
    }
}
