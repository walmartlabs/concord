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

import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.Condition;
import org.jooq.Configuration;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.*;

/**
 * Scope lives on the queue row, outside process files, checkpoints and payloads.
 * No update or clear operation is provided. Children retain the original binding.
 */
public class ExternalProcessScopeDao extends AbstractDao {

    private final ConcordObjectMapper objectMapper;

    @Inject
    public ExternalProcessScopeDao(@MainDB Configuration cfg, ConcordObjectMapper objectMapper) {
        super(cfg);
        this.objectMapper = objectMapper;
    }

    public Optional<StoredContext> get(PartialProcessKey key) {
        return dsl().select(PROCESS_QUEUE.INSTANCE_ID, PROCESS_QUEUE.CREATED_AT, PROCESS_QUEUE.INITIATOR_ID,
                        USERS.USER_TYPE, PROCESS_QUEUE.PROJECT_ID, PROCESS_QUEUE.REPO_ID,
                        PROCESS_QUEUE.PARENT_INSTANCE_ID, PROCESS_QUEUE.EXTERNAL_IDENTITY_SCOPE)
                .from(PROCESS_QUEUE)
                .leftJoin(USERS).on(USERS.USER_ID.eq(PROCESS_QUEUE.INITIATOR_ID))
                .where(matches(key))
                .fetchOptional(r -> new StoredContext(new ProcessKey(r.value1(), r.value2()), r.value3(),
                        r.value4() == null ? null : UserType.valueOf(r.value4()), r.value5(), r.value6(), r.value7(),
                        objectMapper.fromJSONB(r.value8(), ExternalProcessScope.class)));
    }

    /**
     * Internal initialization primitive, not an execution permit. The caller must
     * supply a verified actor and integration, using the root queue insertion
     * transaction; no REST or payload caller exists.
     */
    public void bindRoot(DSLContext tx, ProcessKey key, ExternalProcessScope scope) {
        Objects.requireNonNull(scope, "scope");
        var process = tx.selectFrom(PROCESS_QUEUE).where(matches(key)).forUpdate().fetchOne();
        if (process == null) {
            throw new UnauthorizedException("Cannot bind delegation scope to a missing process");
        }
        if (process.getParentInstanceId() != null
                || !scope.userId().equals(process.getInitiatorId())
                || !scope.projectId().equals(process.getProjectId())
                || !scope.repositoryId().equals(process.getRepoId())) {
            throw new UnauthorizedException("Delegation scope does not match the root process integration");
        }
        assertIdentityBinding(tx, scope);
        assertSourceIntegration(tx, scope);
        var existing = objectMapper.fromJSONB(process.getExternalIdentityScope(), ExternalProcessScope.class);
        if (existing != null) {
            if (!scope.equals(existing)) {
                throw new UnauthorizedException("Process delegation scope cannot be replaced");
            }
            return;
        }
        if (!"NEW".equals(process.getCurrentStatus()) && !"PREPARING".equals(process.getCurrentStatus())) {
            throw new UnauthorizedException("Delegation scope must be bound before process execution");
        }
        if (tx.fetchExists(tx.selectOne().from(PROCESS_QUEUE)
                .where(PROCESS_QUEUE.PARENT_INSTANCE_ID.eq(key.getInstanceId())))) {
            throw new UnauthorizedException("Delegation scope must be bound before creating children");
        }
        tx.update(PROCESS_QUEUE).set(PROCESS_QUEUE.EXTERNAL_IDENTITY_SCOPE, objectMapper.toJSONB(scope))
                .where(matches(key)).execute();
    }

    /**
     * Called in the same transaction as the child queue insert. Never selects a
     * new policy binding for a child or accepts a scope from child arguments.
     */
    public void inherit(DSLContext tx, UUID parentId, ProcessKey childKey) {
        if (parentId == null) {
            return;
        }
        var parent = tx.selectFrom(PROCESS_QUEUE).where(PROCESS_QUEUE.INSTANCE_ID.eq(parentId))
                .forShare().fetchOne();
        if (parent == null) {
            throw new UnauthorizedException("Cannot inherit delegation scope from a missing parent process");
        }
        var scope = objectMapper.fromJSONB(parent.getExternalIdentityScope(), ExternalProcessScope.class);
        if (scope == null) {
            if (tx.fetchExists(tx.selectOne().from(USERS).where(USERS.USER_ID.eq(parent.getInitiatorId()))
                    .and(USERS.USER_TYPE.eq(UserType.EXTERNAL.name())))) {
                throw new UnauthorizedException("External parent process has no trusted delegation scope");
            }
            return;
        }
        var child = tx.selectFrom(PROCESS_QUEUE).where(matches(childKey)).forUpdate().fetchSingle();
        var parentRepositoryMatches = scope.repositoryId().equals(parent.getRepoId())
                || (parent.getRepoId() == null && parent.getParentInstanceId() != null);
        if (!parentId.equals(child.getParentInstanceId())
                || !scope.userId().equals(parent.getInitiatorId())
                || !scope.projectId().equals(parent.getProjectId())
                || !parentRepositoryMatches
                || !scope.userId().equals(child.getInitiatorId())
                || !scope.projectId().equals(child.getProjectId())
                || (child.getRepoId() != null && !scope.repositoryId().equals(child.getRepoId()))) {
            throw new UnauthorizedException("Child process cannot change its parent's delegation binding");
        }
        var existing = objectMapper.fromJSONB(child.getExternalIdentityScope(), ExternalProcessScope.class);
        if (existing != null && !scope.equals(existing)) {
            throw new UnauthorizedException("Child process delegation scope cannot be replaced");
        }
        assertIdentityBinding(tx, scope);
        assertSourceIntegration(tx, scope);
        tx.update(PROCESS_QUEUE).set(PROCESS_QUEUE.EXTERNAL_IDENTITY_SCOPE, objectMapper.toJSONB(scope))
                .where(matches(childKey)).execute();
    }

    public void assertSourceIntegration(ExternalProcessScope scope) {
        assertSourceIntegration(dsl(), scope);
    }

    private static void assertSourceIntegration(DSLContext tx, ExternalProcessScope scope) {
        if (!tx.fetchExists(tx.selectOne().from(REPOSITORIES)
                .join(PROJECTS).on(PROJECTS.PROJECT_ID.eq(REPOSITORIES.PROJECT_ID))
                .where(REPOSITORIES.REPO_ID.eq(scope.repositoryId()))
                .and(PROJECTS.PROJECT_ID.eq(scope.projectId())).and(PROJECTS.ORG_ID.eq(scope.orgId())))) {
            throw new UnauthorizedException("Delegation source integration no longer exists or has changed");
        }
    }

    private static void assertIdentityBinding(DSLContext tx, ExternalProcessScope scope) {
        var ref = scope.identity();
        if (!tx.fetchExists(tx.selectOne().from(EXTERNAL_IDENTITIES)
                .join(USERS).on(USERS.USER_ID.eq(EXTERNAL_IDENTITIES.USER_ID))
                .where(EXTERNAL_IDENTITIES.PROVIDER_ID.eq(ref.providerId()))
                .and(EXTERNAL_IDENTITIES.AUTHORITY.eq(ref.authority()))
                .and(EXTERNAL_IDENTITIES.SUBJECT_ID.eq(ref.subjectId()))
                .and(EXTERNAL_IDENTITIES.USER_ID.eq(scope.userId()))
                .and(USERS.USER_TYPE.eq(UserType.EXTERNAL.name())))) {
            throw new UnauthorizedException("Process delegation identity binding no longer matches");
        }
    }

    private static Condition matches(PartialProcessKey key) {
        Objects.requireNonNull(key, "key");
        var condition = PROCESS_QUEUE.INSTANCE_ID.eq(Objects.requireNonNull(key.getInstanceId(), "instanceId"));
        return key instanceof ProcessKey full ? condition.and(PROCESS_QUEUE.CREATED_AT.eq(full.getCreatedAt())) : condition;
    }

    public record StoredContext(ProcessKey processKey, UUID initiatorId, UserType userType, UUID projectId,
                                UUID repositoryId, UUID parentId, ExternalProcessScope scope) {
    }
}
