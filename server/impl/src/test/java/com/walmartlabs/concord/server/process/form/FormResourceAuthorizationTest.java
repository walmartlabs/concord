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
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FormResourceAuthorizationTest {

    private final UUID instanceId = UUID.randomUUID();
    private final ProcessStateManager stateManager = mock(ProcessStateManager.class);
    private final ProcessAccessManager accessManager = mock(ProcessAccessManager.class);
    private final FormResourceV1 v1 = mock(FormResourceV1.class);
    private final FormResourceV2 v2 = mock(FormResourceV2.class);
    private final FormResource resource = new FormResource(stateManager, accessManager, v1, v2);
    private final String formsPath = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
            Constants.Files.JOB_STATE_DIR_NAME, Constants.Files.JOB_FORMS_V2_DIR_NAME);

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void deniedListDoesNotInspectStateOrDispatch() {
        doThrow(new UnauthorizedException("no process access"))
                .when(accessManager).assertAccess(instanceId, Collections.emptySet());

        assertThrows(UnauthorizedException.class, () -> resource.list(instanceId));

        verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        verifyNoInteractions(stateManager, v1, v2);
    }

    @Test
    void deniedGetDoesNotInspectStateOrDispatch() {
        doThrow(new UnauthorizedException("no process access"))
                .when(accessManager).assertAccess(instanceId, Collections.emptySet());

        assertThrows(UnauthorizedException.class, () -> resource.get(instanceId, "approval"));

        verify(accessManager).assertAccess(instanceId, Collections.emptySet());
        verifyNoInteractions(stateManager, v1, v2);
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

    @Test
    void realProcessAccessManagerRejectsCrossTenantReadsBeforeStateAccess() {
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
        var securedResource = new FormResource(stateManager, realAccessManager, v1, v2);

        assertThrows(UnauthorizedException.class, () -> securedResource.list(instanceId));
        assertThrows(UnauthorizedException.class, () -> securedResource.get(instanceId, "approval"));

        verify(projectAccessManager, times(2))
                .assertAccess(orgId, projectId, null, ResourceAccessLevel.READER, false);
        verifyNoInteractions(stateManager, v1, v2);
    }
}
