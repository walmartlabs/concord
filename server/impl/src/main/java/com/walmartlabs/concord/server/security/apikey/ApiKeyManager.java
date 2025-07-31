package com.walmartlabs.concord.server.security.apikey;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.db.PgUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.jooq.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

public class ApiKeyManager {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyManager.class);

    private final ApiKeyDao apiKeyDao;
    private final AuditLog auditLog;
    private final ApiKeyConfiguration cfg;
    private final UserManager userManager;

    @Inject
    public ApiKeyManager(ApiKeyConfiguration cfg,
                         UserManager userManager,
                         ApiKeyDao apiKeyDao,
                         AuditLog auditLog) {

        this.cfg = requireNonNull(cfg);
        this.userManager = requireNonNull(userManager);
        this.apiKeyDao = requireNonNull(apiKeyDao);
        this.auditLog = requireNonNull(auditLog);
    }


    public CreateApiKeyResponse create(CreateApiKeyRequest req) {
        String key = assertKeyValue(req);

        UUID userId = assertUserId(req.getUserId());
        if (userId == null) {
            userId = assertUsername(req.getUsername(), req.getUserDomain(), req.getUserType());
        }
        
        assertOwner(userId);

        String name = trim(req.getName());
        if (name == null || name.isEmpty()) {
            // auto generate the name
            name = "key-" + UUID.randomUUID();
        } else {
            if (!name.matches(ConcordKey.PATTERN)) {
                throw new ValidationErrorsException("Invalid API key name. Must match " + ConcordKey.PATTERN);
            }

            if (apiKeyDao.getId(userId, name) != null) {
                throw new ValidationErrorsException("API key with name '" + name + "' already exists");
            }
        }

        return createApiKey(userId, name, key);
    }

    public CreateApiKeyResponse createOrUpdate(CreateApiKeyRequest req) {
        String key = assertKeyValue(req);

        UUID userId = assertUserId(req.getUserId());
        if (userId == null) {
            userId = assertUsername(req.getUsername(), req.getUserDomain(), req.getUserType());
        }

        if (userId == null) {
            userId = UserPrincipal.assertCurrent().getId();
        }

        assertOwner(userId);

        String name = trim(req.getName());
        if (name == null || name.isEmpty()) {
            // auto generate the name
            name = "key#" + UUID.randomUUID();
        }

        UUID apiKeyId = apiKeyDao.getId(userId, name);
        if (apiKeyId == null) {
            return createApiKey(userId, name, key);
        } else {
            return updateApiKey(apiKeyId, userId, name, key);
        }
    }

    public CreateApiKeyResponse createApiKey(UUID userId, String name, @Nullable String key) {
        if (key == null) {
            key = apiKeyDao.newApiKey();
        }

        OffsetDateTime expiredAt = null;
        if (cfg.isExpirationEnabled()) {
            expiredAt = OffsetDateTime.now().plusDays(cfg.getExpirationPeriod().toDays());
        }

        UUID id;
        try {
            id = apiKeyDao.insert(userId, key, name, expiredAt);
        } catch (DataAccessException e) {
            if (PgUtils.isUniqueViolationError(e)) {
                log.warn("create ['{}'] -> duplicate name error: {}", name, e.getMessage());
                throw new ValidationErrorsException("Duplicate API key name: " + name);
            }

            throw e;
        }

        auditLog.add(AuditObject.API_KEY, AuditAction.CREATE)
                .field("id", id)
                .field("name", name)
                .field("expiredAt", expiredAt)
                .field("userId", userId)
                .log();

        return new CreateApiKeyResponse(id, name, key, OperationResult.CREATED);
    }

    public CreateApiKeyResponse updateApiKey(UUID id, UUID userId, String name, @Nullable String key) {
        if (key == null) {
            key = apiKeyDao.newApiKey();
        }

        OffsetDateTime expiredAt = null;
        if (cfg.isExpirationEnabled()) {
            expiredAt = OffsetDateTime.now().plusDays(cfg.getExpirationPeriod().toDays());
        }

        apiKeyDao.update(id, key, expiredAt);

        auditLog.add(AuditObject.API_KEY, AuditAction.UPDATE)
                .field("id", id)
                .field("name", name)
                .field("expiredAt", expiredAt)
                .field("userId", userId)
                .log();

        return new CreateApiKeyResponse(id, name, key, OperationResult.UPDATED);
    }

    public void deleteById(UUID id) {
        UUID userId = apiKeyDao.getUserId(id);
        if (userId == null) {
            throw new ValidationErrorsException("API key not found: " + id);
        }

        assertOwner(userId);

        apiKeyDao.delete(id);

        auditLog.add(AuditObject.API_KEY, AuditAction.DELETE)
                .field("id", id)
                .log();
    }

    public List<ApiKeyEntry> list(@Nullable UUID userId) {
        UUID effectiveUserId = userId;
        if (effectiveUserId == null) {
            effectiveUserId = UserPrincipal.assertCurrent().getId();
        }

        assertOwner(effectiveUserId);

        return apiKeyDao.list(effectiveUserId);
    }

    private UUID assertUsername(String username, String domain, UserType type) {
        if (username == null) {
            return null;
        }

        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        return userManager.getId(username, domain, type)
                .orElseThrow(() -> new ConcordApplicationException("User not found: " + username));
    }

    private UUID assertUserId(UUID userId) {
        if (userId == null) {
            return null;
        }

        if (userManager.get(userId).isEmpty()) {
            throw new ValidationErrorsException("User not found: " + userId);
        }

        return userId;
    }

    private static String assertKeyValue(CreateApiKeyRequest req) {
        String key = req.getKey();

        if (key != null && !Permission.API_KEY_SPECIFY_VALUE.isPermitted()) {
            throw new UnauthorizedException("Not allowed to specify the API key value.");
        }

        return key;
    }

    private static void assertOwner(UUID userId) {
        if (Roles.isAdmin()) {
            // admin users can manage other user's keys
            return;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (!userId.equals(p.getId())) {
            throw new UnauthorizedException("Operation is not permitted");
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }

        return s.trim();
    }
}
