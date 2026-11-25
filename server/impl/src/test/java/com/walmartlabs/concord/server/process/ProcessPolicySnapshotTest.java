package com.walmartlabs.concord.server.process;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.ZipService;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.cfg.ProcessConfiguration;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.project.EncryptedProjectValueManager;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogAccessManager;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.waits.ProcessWaitManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserEntry;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.POLICY_FILE_PATH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessPolicySnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProcessKey processKey = ProcessKey.random();
    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID initiatorId = UUID.randomUUID();
    private final PolicyManager policyManager = mock(PolicyManager.class);
    private final ProcessStateManager stateManager = mock(ProcessStateManager.class);
    private ProcessResource resource;

    @BeforeEach
    void setUp() {
        UserEntry user = mock(UserEntry.class);
        when(user.getId()).thenReturn(initiatorId);
        UserPrincipal principal = new UserPrincipal("test", user);
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        ThreadContext.bind(securityManager);
        ThreadContext.bind(new Subject.Builder(securityManager)
                .principals(new SimplePrincipalCollection(principal, "test")).authenticated(true).buildSubject());

        ProcessEntry entry = mock(ProcessEntry.class);
        when(entry.instanceId()).thenReturn(processKey.getInstanceId());
        when(entry.createdAt()).thenReturn(processKey.getCreatedAt());
        when(entry.orgId()).thenReturn(orgId);
        when(entry.projectId()).thenReturn(projectId);
        when(entry.initiatorId()).thenReturn(initiatorId);
        ProcessQueueManager queueManager = mock(ProcessQueueManager.class);
        when(queueManager.get(any(PartialProcessKey.class))).thenReturn(entry);

        when(stateManager.export(eq(processKey), any())).thenAnswer(invocation -> {
            ProcessStateManager.ItemConsumer consumer = invocation.getArgument(1);
            consumer.accept(POLICY_FILE_PATH, 0100644, new ByteArrayInputStream("stale-policy".getBytes()));
            consumer.accept(".concord/state", 0100600, new ByteArrayInputStream("state".getBytes()));
            return true;
        });

        resource = new ProcessResource(mock(ProcessWaitManager.class), mock(ProcessManager.class),
                mock(ProcessQueueDao.class), queueManager, mock(PayloadManager.class), stateManager,
                mock(SecretStoreConfiguration.class), mock(EncryptedProjectValueManager.class),
                mock(ProjectAccessManager.class), mock(ProcessKeyCache.class), objectMapper,
                mock(ProcessConfiguration.class), mock(ProcessLogManager.class), mock(ProcessLogAccessManager.class),
                mock(ProcessLogManager.class), policyManager, mock(UuidGenerator.class), mock(ZipService.class), null);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void generatesFreshPolicyOnEveryDownloadWithoutPersistingIt() throws Exception {
        PolicyEngine first = policy("first");
        PolicyEngine second = policy("second");
        when(policyManager.get(orgId, projectId, initiatorId)).thenReturn(first, second);

        Map<String, byte[]> firstSnapshot = download();
        Map<String, byte[]> secondSnapshot = download();

        assertEquals(objectMapper.valueToTree(first.getRules()), objectMapper.readTree(firstSnapshot.get(POLICY_FILE_PATH)));
        assertEquals(objectMapper.valueToTree(second.getRules()), objectMapper.readTree(secondSnapshot.get(POLICY_FILE_PATH)));
        assertArrayEquals("state".getBytes(), secondSnapshot.get(".concord/state"));
        verify(policyManager, times(2)).get(orgId, projectId, initiatorId);
        verify(stateManager, times(2)).export(eq(processKey), any());
        verifyNoMoreInteractions(stateManager);
    }

    @Test
    void omitsStalePolicyWhenNoPolicyApplies() throws Exception {
        Map<String, byte[]> snapshot = download();

        assertFalse(snapshot.containsKey(POLICY_FILE_PATH));
        assertArrayEquals("state".getBytes(), snapshot.get(".concord/state"));
    }

    @Test
    void generatesPolicyForSingleFileDownloadWithoutReadingState() throws Exception {
        PolicyEngine policy = policy("current");
        when(policyManager.get(orgId, projectId, initiatorId)).thenReturn(policy);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (Response response = resource.downloadStateFile(processKey.getInstanceId(), POLICY_FILE_PATH)) {
            ((StreamingOutput) response.getEntity()).write(out);
        }

        assertEquals(objectMapper.valueToTree(policy.getRules()), objectMapper.readTree(out.toByteArray()));
        verifyNoInteractions(stateManager);
    }

    @Test
    void singleFileDownloadReturnsNotFoundWhenNoPolicyApplies() {
        ConcordApplicationException e = assertThrows(ConcordApplicationException.class,
                () -> resource.downloadStateFile(processKey.getInstanceId(), POLICY_FILE_PATH));

        assertEquals(404, e.getResponse().getStatus());
        verifyNoInteractions(stateManager);
    }

    private Map<String, byte[]> download() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Response response = resource.downloadState(processKey.getInstanceId())) {
            ((StreamingOutput) response.getEntity()).write(out);
        }
        Map<String, byte[]> result = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                assertNull(result.put(entry.getName(), zip.readAllBytes()), "Duplicate ZIP entry");
            }
        }
        return result;
    }

    private static PolicyEngine policy(String version) {
        return new PolicyEngine(version, PolicyEngineRules.builder().processCfg(Map.of("version", version)).build());
    }
}
