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

import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.TestObjectMapper;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.user.ExternalIdentityDao;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static org.junit.jupiter.api.Assertions.*;

class ExternalProcessScopeDaoTest {

    private static PostgreSQLContainer<?> db;
    private static Configuration configuration;
    private DSLContext tx;
    private ExternalIdentityDao identities;
    private ExternalProcessScopeDao scopes;
    private ProcessQueueDao queue;
    private ExternalProcessScope scope;

    @BeforeAll
    static void startDatabase() {
        db = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("db.image"))
                .asCompatibleSubstituteFor("postgres"));
        db.start();
        DataSourceUtils.migrateDb(new DatabaseConfiguration() {
            public String url() { return db.getJdbcUrl(); }
            public String username() { return db.getUsername(); }
            public String password() { return db.getPassword(); }
            public int maxPoolSize() { return 10; }
            public Duration maxLifetime() { return Duration.ofMinutes(1); }
            public Map<String, Object> changeLogParameters() {
                var fakeSecret = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
                return Map.of("createExtensionAvailable", true, "defaultAdminToken", fakeSecret,
                        "skipAdminTokenGeneration", true, "defaultAgentToken", fakeSecret,
                        "skipAgentTokenGeneration", true, "secretStoreSalt", fakeSecret, "serverPassword", fakeSecret);
            }
        }, new MainDBChangeLogProvider());
        var ds = new PGSimpleDataSource();
        ds.setUrl(db.getJdbcUrl());
        ds.setUser(db.getUsername());
        ds.setPassword(db.getPassword());
        configuration = DataSourceUtils.createJooqConfiguration(ds);
    }

    @AfterAll
    static void stopDatabase() {
        if (db != null) {
            db.stop();
        }
    }

    @BeforeEach
    void setUp() {
        tx = DSL.using(configuration);
        identities = new ExternalIdentityDao(configuration, new UuidGenerator());
        var mapper = new ConcordObjectMapper(TestObjectMapper.INSTANCE);
        scopes = new ExternalProcessScopeDao(configuration, mapper);
        queue = new ProcessQueueDao(configuration, mapper);
        var ref = new ExternalIdentityRef("provider", "authority", UUID.randomUUID().toString());
        var userId = identities.getOrCreate(ref, null, null);
        var orgId = UUID.randomUUID();
        var projectId = UUID.randomUUID();
        var repositoryId = UUID.randomUUID();
        tx.insertInto(ORGANIZATIONS).set(ORGANIZATIONS.ORG_ID, orgId)
                .set(ORGANIZATIONS.ORG_NAME, orgId.toString()).execute();
        tx.insertInto(PROJECTS).set(PROJECTS.PROJECT_ID, projectId).set(PROJECTS.ORG_ID, orgId)
                .set(PROJECTS.PROJECT_NAME, projectId.toString()).execute();
        tx.insertInto(REPOSITORIES).set(REPOSITORIES.REPO_ID, repositoryId).set(REPOSITORIES.PROJECT_ID, projectId)
                .set(REPOSITORIES.REPO_NAME, "source").set(REPOSITORIES.REPO_BRANCH, "main")
                .set(REPOSITORIES.REPO_URL, "https://github.example/org/repo").execute();
        scope = new ExternalProcessScope(userId, ref, orgId, projectId, repositoryId);
    }

    @Test
    void rootBindingRoundTripsWithExactProcessKeyAndCannotBeReplaced() {
        var root = root();
        assertEquals(scope, scopes.get(root).orElseThrow().scope());
        assertEquals(scope, scopes.get(PartialProcessKey.from(root.getInstanceId())).orElseThrow().scope());
        assertTrue(scopes.get(new ProcessKey(root.getInstanceId(), root.getCreatedAt().minusSeconds(1))).isEmpty());
        var publicEntry = queue.get(root, Set.of());
        assertEquals(scope.userId(), publicEntry.initiatorId());
        assertFalse(TestObjectMapper.INSTANCE.valueToTree(publicEntry).has("externalIdentityScope"));
        queue.tx(tx -> scopes.bindRoot(tx, root, scope));
        var changed = new ExternalProcessScope(scope.userId(), scope.identity(), UUID.randomUUID(),
                scope.projectId(), scope.repositoryId());
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, root, changed)));
        assertEquals(scope, scopes.get(root).orElseThrow().scope());
    }

    @Test
    void failedBindingRollsBackRootInsertionAndRejectsMismatchedMetadata() {
        var root = ProcessKey.random();
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> {
            insert(tx, root, null, scope.userId(), scope.projectId(), null);
            scopes.bindRoot(tx, root, scope);
        }));
        assertTrue(scopes.get(root).isEmpty());
        assertFalse(tx.fetchExists(PROCESS_META, PROCESS_META.INSTANCE_ID.eq(root.getInstanceId())));
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, root, scope)));
    }

    @Test
    void childAndGrandchildInheritOriginalIntegrationWithoutNeedingTheirOwnRepository() {
        var root = root();
        var child = ProcessKey.random();
        queue.tx(tx -> insert(tx, child, root.getInstanceId(), scope.userId(), scope.projectId(), null));
        assertEquals(scope, scopes.get(child).orElseThrow().scope());
        var grandchild = ProcessKey.random();
        queue.tx(tx -> insert(tx, grandchild, child.getInstanceId(), scope.userId(), scope.projectId(), scope.repositoryId()));
        assertEquals(scope, scopes.get(grandchild).orElseThrow().scope());
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, child, scope)));
    }

    @Test
    void childCannotChangeInitiatorProjectOrRepositoryAndInsertionRollsBack() {
        var root = root();
        var anotherUser = identities.getOrCreate(new ExternalIdentityRef("provider", "authority", UUID.randomUUID().toString()),
                null, null);
        var anotherProject = UUID.randomUUID();
        tx.insertInto(PROJECTS).set(PROJECTS.PROJECT_ID, anotherProject).set(PROJECTS.ORG_ID, scope.orgId())
                .set(PROJECTS.PROJECT_NAME, anotherProject.toString()).execute();
        var anotherRepo = UUID.randomUUID();
        tx.insertInto(REPOSITORIES).set(REPOSITORIES.REPO_ID, anotherRepo).set(REPOSITORIES.PROJECT_ID, scope.projectId())
                .set(REPOSITORIES.REPO_NAME, "other").set(REPOSITORIES.REPO_BRANCH, "main")
                .set(REPOSITORIES.REPO_URL, "https://github.example/org/other").execute();
        assertChildDenied(root, anotherUser, scope.projectId(), null);
        assertChildDenied(root, scope.userId(), anotherProject, null);
        assertChildDenied(root, scope.userId(), scope.projectId(), anotherRepo);
        assertChildDenied(root, scope.userId(), null, null);
    }

    @Test
    void externalParentWithoutScopeCannotProduceUnscopedChild() {
        var root = ProcessKey.random();
        queue.tx(tx -> insert(tx, root, null, scope.userId(), scope.projectId(), scope.repositoryId()));
        assertChildDenied(root, scope.userId(), scope.projectId(), null);
    }

    @Test
    void missingParentCannotSilentlyDropInheritedScope() {
        assertChildDenied(ProcessKey.random(), scope.userId(), scope.projectId(), null);
    }

    @Test
    void rebindingSameUserToAnotherExternalIdentityCannotReplaceScope() {
        var root = root();
        var replacementRef = new ExternalIdentityRef(scope.identity().providerId(), scope.identity().authority(), "replacement");
        tx.update(EXTERNAL_IDENTITIES).set(EXTERNAL_IDENTITIES.SUBJECT_ID, replacementRef.subjectId())
                .where(EXTERNAL_IDENTITIES.USER_ID.eq(scope.userId())).execute();
        var replacementScope = new ExternalProcessScope(scope.userId(), replacementRef, scope.orgId(),
                scope.projectId(), scope.repositoryId());
        var failure = assertThrows(UnauthorizedException.class, () ->
                queue.tx(tx -> scopes.bindRoot(tx, root, replacementScope)));
        assertTrue(failure.getMessage().contains("cannot be replaced"));
        assertEquals(scope, scopes.get(root).orElseThrow().scope());
    }

    @Test
    void recreatedIdentityCannotBeBoundToOldProcessOrInherited() {
        var root = root();
        tx.deleteFrom(USERS).where(USERS.USER_ID.eq(scope.userId())).execute();
        var replacement = identities.getOrCreate(scope.identity(), null, null);
        assertNotEquals(scope.userId(), replacement);
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, root, scope)));
        assertChildDenied(root, replacement, scope.projectId(), null);
        assertEquals(scope, scopes.get(root).orElseThrow().scope());
    }

    @Test
    void queueScopeSurvivesProcessStateReplacementAndStatusChangesButNotQueueDeletion() {
        var root = root();
        tx.insertInto(PROCESS_STATE).set(PROCESS_STATE.INSTANCE_ID, root.getInstanceId())
                .set(PROCESS_STATE.INSTANCE_CREATED_AT, root.getCreatedAt())
                .set(PROCESS_STATE.ITEM_PATH, ".concord/current_user").set(PROCESS_STATE.ITEM_DATA, new byte[]{1}).execute();
        tx.deleteFrom(PROCESS_STATE).where(PROCESS_STATE.INSTANCE_ID.eq(root.getInstanceId())).execute();
        tx.update(PROCESS_QUEUE).set(PROCESS_QUEUE.CURRENT_STATUS, ProcessStatus.NEW.name())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(root.getInstanceId())).execute();
        assertEquals(scope, scopes.get(root).orElseThrow().scope());
        tx.deleteFrom(PROCESS_QUEUE).where(PROCESS_QUEUE.INSTANCE_ID.eq(root.getInstanceId())).execute();
        assertTrue(scopes.get(root).isEmpty());
    }

    @Test
    void sourceIntegrationAndLateBindingAreRejected() {
        var root = ProcessKey.random();
        queue.tx(tx -> insert(tx, root, null, scope.userId(), scope.projectId(), scope.repositoryId()));
        tx.update(PROCESS_QUEUE).set(PROCESS_QUEUE.CURRENT_STATUS, ProcessStatus.RUNNING.name())
                .where(PROCESS_QUEUE.INSTANCE_ID.eq(root.getInstanceId())).execute();
        assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, root, scope)));
        var boundRoot = root();
        tx.update(PROJECTS).set(PROJECTS.ORG_ID, UUID.fromString("0fac1b18-d179-11e7-b3e7-d7df4543ed4f"))
                .where(PROJECTS.PROJECT_ID.eq(scope.projectId())).execute();
        assertThrows(UnauthorizedException.class, () -> scopes.assertSourceIntegration(scope));
        assertChildDenied(boundRoot, scope.userId(), scope.projectId(), null);
    }

    @Test
    void rootCannotGainScopeAfterUnscopedChildrenAlreadyExist() {
        var root = ProcessKey.random();
        queue.tx(tx -> insert(tx, root, null, scope.userId(), scope.projectId(), scope.repositoryId()));
        var child = ProcessKey.random();
        tx.insertInto(PROCESS_QUEUE).set(PROCESS_QUEUE.INSTANCE_ID, child.getInstanceId())
                .set(PROCESS_QUEUE.CREATED_AT, child.getCreatedAt())
                .set(PROCESS_QUEUE.LAST_UPDATED_AT, child.getCreatedAt())
                .set(PROCESS_QUEUE.PARENT_INSTANCE_ID, root.getInstanceId())
                .set(PROCESS_QUEUE.CURRENT_STATUS, ProcessStatus.NEW.name()).execute();
        var failure = assertThrows(UnauthorizedException.class, () -> queue.tx(tx -> scopes.bindRoot(tx, root, scope)));
        assertTrue(failure.getMessage().contains("before creating children"));
        assertNull(scopes.get(root).orElseThrow().scope());
    }

    @Test
    void ordinaryAccountChildrenRemainUnscopedAndAllowDifferentInitiators() {
        var userId = UUID.randomUUID();
        var childUserId = UUID.randomUUID();
        tx.insertInto(USERS).set(USERS.USER_ID, userId).set(USERS.USERNAME, userId.toString())
                .set(USERS.USER_TYPE, UserType.LOCAL.name()).execute();
        tx.insertInto(USERS).set(USERS.USER_ID, childUserId).set(USERS.USERNAME, childUserId.toString())
                .set(USERS.USER_TYPE, UserType.LDAP.name()).execute();
        var root = ProcessKey.random();
        var child = ProcessKey.random();
        queue.tx(tx -> insert(tx, root, null, userId, scope.projectId(), scope.repositoryId()));
        queue.tx(tx -> insert(tx, child, root.getInstanceId(), childUserId, scope.projectId(), null));
        assertNull(scopes.get(root).orElseThrow().scope());
        assertNull(scopes.get(child).orElseThrow().scope());
    }

    private ProcessKey root() {
        var root = ProcessKey.random();
        queue.tx(tx -> {
            insert(tx, root, null, scope.userId(), scope.projectId(), scope.repositoryId());
            scopes.bindRoot(tx, root, scope);
        });
        return root;
    }

    private void assertChildDenied(ProcessKey parent, UUID userId, UUID projectId, UUID repoId) {
        var child = ProcessKey.random();
        assertThrows(UnauthorizedException.class, () ->
                queue.tx(tx -> insert(tx, child, parent.getInstanceId(), userId, projectId, repoId)));
        assertTrue(scopes.get(child).isEmpty());
        assertFalse(tx.fetchExists(PROCESS_META, PROCESS_META.INSTANCE_ID.eq(child.getInstanceId())));
    }

    private void insert(DSLContext tx, ProcessKey key, UUID parentId, UUID userId, UUID projectId, UUID repoId) {
        queue.insert(tx, key, ProcessStatus.NEW, ProcessKind.DEFAULT, parentId, projectId, repoId,
                null, null, userId, Map.of(), null);
    }
}
