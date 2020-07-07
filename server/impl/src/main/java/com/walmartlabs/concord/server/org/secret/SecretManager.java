package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.*;
import com.walmartlabs.concord.server.org.project.DiffUtils;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.SecretDao.SecretDataEntry;
import com.walmartlabs.concord.server.org.secret.provider.SecretStoreProvider;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.policy.EntityAction;
import com.walmartlabs.concord.server.policy.EntityType;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.policy.PolicyUtils;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.authz.UnauthorizedException;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.Tables.SECRETS;
import static com.walmartlabs.concord.server.org.secret.SecretDao.InsertMode.INSERT;

@Named
public class SecretManager {

    private final PolicyManager policyManager;
    private final AuditLog auditLog;
    private final OrganizationManager orgManager;
    private final ProcessQueueManager processQueueManager;
    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;
    private final SecretStoreProvider secretStoreProvider;
    private final UserDao userDao;
    private final ProjectAccessManager projectAccessManager;
    private final RepositoryDao repositoryDao;

    @Inject
    public SecretManager(PolicyManager policyManager,
                         AuditLog auditLog,
                         OrganizationManager orgManager,
                         ProcessQueueManager processQueueManager,
                         SecretDao secretDao,
                         SecretStoreConfiguration secretCfg,
                         SecretStoreProvider secretStoreProvider,
                         UserDao userDao,
                         ProjectAccessManager projectAccessManager,
                         RepositoryDao repositoryDao) {

        this.policyManager = policyManager;
        this.processQueueManager = processQueueManager;
        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
        this.orgManager = orgManager;
        this.userDao = userDao;
        this.secretStoreProvider = secretStoreProvider;
        this.auditLog = auditLog;
        this.projectAccessManager = projectAccessManager;
        this.repositoryDao = repositoryDao;
    }

    @WithTimer
    public SecretEntry assertAccess(UUID orgId, UUID secretId, String secretName, ResourceAccessLevel level, boolean orgMembersOnly) {
        if (secretId == null && (orgId == null || secretName == null)) {
            throw new ValidationErrorsException("Secret ID or an organization ID and a secret name is required");
        }

        SecretEntry e = null;

        if (secretId != null) {
            e = secretDao.get(secretId);
            if (e == null) {
                throw new WebApplicationException("Secret not found: " + secretId, Status.NOT_FOUND);
            }
        }

        if (e == null) {
            e = secretDao.getByName(orgId, secretName);
            if (e == null) {
                throw new WebApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
            }
        }

        if (Roles.isAdmin()) {
            // an admin can access any secret
            return e;
        }

        if (level == ResourceAccessLevel.READER && (Roles.isGlobalReader() || Roles.isGlobalWriter())) {
            return e;
        } else if (level == ResourceAccessLevel.WRITER && Roles.isGlobalWriter()) {
            return e;
        }

        UserPrincipal p = UserPrincipal.assertCurrent();

        EntityOwner owner = e.getOwner();
        if (owner != null && p.getId().equals(owner.id())) {
            // the owner can do anything with his secrets
            return e;
        }

        if (orgMembersOnly && e.getVisibility() == SecretVisibility.PUBLIC
                && level == ResourceAccessLevel.READER
                && userDao.isInOrganization(p.getId(), e.getOrgId())) {
            // organization members can access any public secret in the same organization
            return e;
        }

        OrganizationEntry org = orgManager.assertAccess(e.getOrgId(), false);
        if (ResourceAccessUtils.isSame(p, org.getOwner())) {
            // the org owner can do anything with the org's secrets
            return e;
        }

        if (orgMembersOnly || e.getVisibility() != SecretVisibility.PUBLIC) {
            // we need to check the resource's access level if the access is limited to
            // the organization's members or the secret is not public
            if (!secretDao.hasAccessLevel(e.getId(), p.getId(), ResourceAccessLevel.atLeast(level))) {
                throw new UnauthorizedException("The current user doesn't have " +
                        "the necessary access level (" + level + ") to the secret: " + e.getName());
            }
        }

        return e;
    }

    /**
     * Generates and stores a new SSH key pair.
     */
    public DecryptedKeyPair createKeyPair(UUID orgId, UUID projectId, String name, String storePassword,
                                          SecretVisibility visibility, String secretStoreType) {

        orgManager.assertAccess(orgId, true);

        KeyPair k = KeyPairUtils.create(secretCfg.getKeySize());
        UUID id = create(name, orgId, projectId, k, storePassword, visibility, secretStoreType, INSERT);
        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    /**
     * Stores a new SSH key pair using the provided public and private keys.
     */
    public DecryptedKeyPair createKeyPair(UUID orgId, UUID projectId, String name, String storePassword,
                                          InputStream publicKey,
                                          InputStream privateKey, SecretVisibility visibility,
                                          String secretStoreType) throws IOException {

        orgManager.assertAccess(orgId, true);

        KeyPair k = KeyPairUtils.create(publicKey, privateKey);
        UUID id = create(name, orgId, projectId, k, storePassword, visibility, secretStoreType, INSERT);

        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    /**
     * Stores a new username and password secret.
     */
    public DecryptedUsernamePassword createUsernamePassword(UUID orgId, UUID projectId, String name, String storePassword,
                                                            String username, char[] password, SecretVisibility visibility,
                                                            String secretStoreType) {

        orgManager.assertAccess(orgId, true);

        UsernamePassword p = new UsernamePassword(username, password);
        UUID id = create(name, orgId, projectId, p, storePassword, visibility, secretStoreType, INSERT);
        return new DecryptedUsernamePassword(id);
    }

    /**
     * Stores a new single value secret.
     */
    public DecryptedBinaryData createBinaryData(UUID orgId, UUID projectId, String name, String storePassword,
                                                InputStream data, SecretVisibility visibility,
                                                String storeType) throws IOException {
        return createBinaryData(orgId, projectId, name, storePassword, data, visibility, storeType, INSERT);
    }

    /**
     * Stores a new single value secret.
     */
    public DecryptedBinaryData createBinaryData(UUID orgId, UUID projectId, String name, String storePassword,
                                                InputStream data, SecretVisibility visibility,
                                                String storeType, SecretDao.InsertMode insertMode) throws IOException {

        orgManager.assertAccess(orgId, true);

        int maxSecretDataSize = secretStoreProvider.getMaxSecretDataSize();
        InputStream limitedDataInputStream = ByteStreams.limit(data, maxSecretDataSize + 1L);
        BinaryDataSecret d = new BinaryDataSecret(ByteStreams.toByteArray(limitedDataInputStream));
        if (d.getData().length > maxSecretDataSize) {
            throw new IllegalArgumentException("File size exceeds limit of " + maxSecretDataSize + " bytes");
        }
        UUID id = create(name, orgId, projectId, d, storePassword, visibility, storeType, insertMode);
        return new DecryptedBinaryData(id);
    }

    /**
     * Decrypts a stored SSH key pair.
     */
    public DecryptedKeyPair getKeyPair(AccessScope accessScope, UUID orgId, String name) {
        DecryptedSecret e = getSecret(accessScope, orgId, name, null, SecretType.KEY_PAIR);
        if (e == null) {
            return null;
        }

        Secret s = e.getSecret();
        KeyPair k = (KeyPair) s;
        return new DecryptedKeyPair(e.getId(), k.getPublicKey());
    }

    /**
     * Updates name and/or visibility and/or data of an existing secret.
     */
    public void update(String orgName, String secretName, SecretUpdateRequest req) {
        SecretEntry e;
        if (req.id() == null) {
            OrganizationEntry org = orgManager.assertAccess(null, orgName, false);
            e = assertAccess(org.getId(), null, secretName, ResourceAccessLevel.WRITER, true);
        } else {
            e = assertAccess(null, req.id(), null, ResourceAccessLevel.WRITER, true);
        }

        String currentPassword = req.storePassword();
        String newPassword = req.newStorePassword();

        if (e.getEncryptedBy() == SecretEncryptedByType.SERVER_KEY && (currentPassword != null || newPassword != null)) {
            throw new ConcordApplicationException("The secret is encrypted with the server's key, the '" + Constants.Multipart.STORE_PASSWORD + "' cannot be changed", Status.BAD_REQUEST);
        }

        Map<String, Object> updated = new HashMap<>();
        byte[] newData = req.data();

        if (newData != null) {
            // updating the data and/or the store password
            if (e.getEncryptedBy() == SecretEncryptedByType.PASSWORD && currentPassword == null) {
                throw new ConcordApplicationException("Updating the secret's data requires the original '" + Constants.Multipart.STORE_PASSWORD + "'", Status.BAD_REQUEST);
            }

            if (e.getType() != SecretType.DATA) {
                throw new ConcordApplicationException("Can't update the data of a non-single value secret (current type: " + e.getType() + ")", Status.BAD_REQUEST);
            }

            // validate the current password
            decryptData(e.getId(), e.getStoreType(), currentPassword);

            updated.put("data", true);
        } else if (newPassword != null) {
            // keeping the old data, just changing the store password
            newData = decryptData(e.getId(), e.getStoreType(), currentPassword);
        }

        String pwd = currentPassword;

        if (newPassword != null && !newPassword.equals(currentPassword)) {
            pwd = req.newStorePassword();
            updated.put(Constants.Multipart.STORE_PASSWORD, true);
        }

        byte[] newEncryptedData;

        if (newData != null) {
            // encrypt the supplied data
            byte[] salt = secretCfg.getSecretStoreSalt();
            newEncryptedData = SecretUtils.encrypt(newData, getPwd(pwd), salt);
        } else {
            newEncryptedData = null;
        }

        OrganizationEntry organizationEntry = null;

        if (req.orgId() != null) {
            organizationEntry = orgManager.assertAccess(req.orgId(), true);
        } else if (req.orgName() != null) {
            organizationEntry = orgManager.assertAccess(req.orgName(), true);
        }

        UUID orgIdUpdate = organizationEntry != null ? organizationEntry.getId() : e.getOrgId();

        UUID projectId = req.projectId();
        String projectName = req.projectName();

        if (!orgIdUpdate.equals(e.getOrgId())) {
            // set the project ID and project name as null when the updated org ID is not same as the current org ID
            // when a secret is changing orgs, the project link must be set to null
            projectId = null;
            projectName = null;
        }

        if (projectName != null && projectName.trim().isEmpty()) {
            // empty project name is same as null project
            projectName = null;
        }

        if (projectId != null || projectName != null) {
            ProjectEntry entry = projectAccessManager.assertAccess(e.getOrgId(), projectId, projectName, ResourceAccessLevel.READER, true);
            projectId = entry.getId();
            if (!entry.getOrgId().equals(e.getOrgId())) {
                throw new ValidationErrorsException("Project -> " + entry.getName() + " does not belong to organization -> " + orgName);
            }
        }

        UUID finalProjectId = projectId;

        secretDao.tx(tx -> {
            if (!orgIdUpdate.equals(e.getOrgId())) {
                // update repository mapping to null when org is changing
                repositoryDao.clearSecretMappingBySecretId(tx, e.getId());
            }

            secretDao.update(tx, e.getId(), req.name(), newEncryptedData, req.visibility(), finalProjectId, orgIdUpdate);
        });

        Map<String, Object> changes = DiffUtils.compare(e, secretDao.get(e.getId()));
        changes.put("updated", updated);

        auditLog.add(AuditObject.SECRET, AuditAction.UPDATE)
                .field("orgId", e.getOrgId())
                .field("secretId", e.getId())
                .field("name", e.getName())
                .field("changes", changes)
                .log();
    }

    /**
     * Removes an existing secret.
     */
    public void delete(UUID orgId, String secretName) {
        SecretEntry e = assertAccess(orgId, null, secretName, ResourceAccessLevel.WRITER, true);

        // delete the content first
        getSecretStore(e.getStoreType()).delete(e.getId());
        // now delete secret information from secret table
        secretDao.delete(e.getId());

        auditLog.add(AuditObject.SECRET, AuditAction.DELETE)
                .field("orgId", e.getOrgId())
                .field("secretId", e.getId())
                .field("name", e.getName())
                .log();
    }

    /**
     * Decrypts and returns an existing secret.
     */
    public DecryptedSecret getSecret(AccessScope accessScope, UUID orgId, String name, String password, SecretType expectedType) {
        SecretDataEntry e = getRaw(accessScope, orgId, name, password);

        if (expectedType != null && e.getType() != expectedType) {
            throw new IllegalArgumentException("Invalid secret type: " + name + ", expected " + expectedType + ", got: " + e.getType());
        }

        Secret s = deserialize(e.getType(), e.getData());
        return new DecryptedSecret(e.getId(), s);
    }

    private byte[] decryptData(UUID secretId, String storeType, String password) {
        byte[] data = getSecretStore(storeType).get(secretId);
        if (data == null) {
            throw new IllegalStateException("Can't find the secret's data in the store " + storeType + " : " + secretId);
        }

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        return SecretUtils.decrypt(data, pwd, salt);
    }

    /**
     * Returns a raw (unencrypted) secret value.
     */
    public SecretDataEntry getRaw(AccessScope accessScope, UUID orgId, String name, String password) {
        SecretEntry e = assertAccess(orgId, null, name, ResourceAccessLevel.READER, false);
        if (e == null) {
            return null;
        }

        // getting a decrypted secret requires additional checks in some cases
        assertProjectScope(accessScope, e);

        SecretEncryptedByType providedEncryptedByType = getEncryptedBy(password);
        assertEncryptedByType(name, providedEncryptedByType, e.getEncryptedBy());

        byte[] ab = decryptData(e.getId(), e.getStoreType(), password);

        auditLog.add(AuditObject.SECRET, AuditAction.ACCESS)
                .field("orgId", e.getOrgId())
                .field("secretId", e.getId())
                .field("name", e.getName())
                .field("type", e.getType())
                .field("scope", accessScope)
                .log();

        return new SecretDataEntry(e, ab);
    }

    /**
     * Decrypts and returns an existing SSH key pair.
     */
    public KeyPair getKeyPair(AccessScope accessScope, UUID orgId, String name, String password) {
        DecryptedSecret e = getSecret(accessScope, orgId, name, password, SecretType.KEY_PAIR);
        if (e == null) {
            return null;
        }

        Secret s = e.getSecret();
        return (KeyPair) s;
    }

    /**
     * Returns a list of secrets for the specified organization.
     */
    public List<SecretEntry> list(UUID orgId, int offset, int limit, String filter) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (Roles.isAdmin() || Roles.isGlobalReader() || Roles.isGlobalWriter()) {
            userId = null;
        }

        return secretDao.list(orgId, userId, SECRETS.SECRET_NAME, true, offset, limit, filter);
    }

    /**
     * Updates a secret's access level for the specified team.
     */
    public void updateAccessLevel(UUID secretId, UUID teamId, ResourceAccessLevel level) {
        assertAccess(null, secretId, null, ResourceAccessLevel.OWNER, true);
        secretDao.upsertAccessLevel(secretId, teamId, level);
    }

    private UUID create(String name, UUID orgId, UUID projectId, Secret s, String password, SecretVisibility visibility, String storeType, SecretDao.InsertMode insertMode) {
        byte[] data;

        SecretType type;
        if (s instanceof KeyPair) {
            type = SecretType.KEY_PAIR;
            data = KeyPair.serialize((KeyPair) s);
        } else if (s instanceof UsernamePassword) {
            type = SecretType.USERNAME_PASSWORD;
            data = UsernamePassword.serialize((UsernamePassword) s);
        } else if (s instanceof BinaryDataSecret) {
            type = SecretType.DATA;
            data = ((BinaryDataSecret) s).getData();
        } else {
            throw new IllegalArgumentException("Unknown secret type: " + s.getClass());
        }

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        byte[] ab = SecretUtils.encrypt(data, pwd, salt);
        SecretEncryptedByType encryptedByType = getEncryptedBy(password);

        storeType = storeType.toLowerCase();

        UserEntry owner = UserPrincipal.assertCurrent().getUser();
        policyManager.checkEntity(orgId, projectId, EntityType.SECRET, EntityAction.CREATE, owner,
                PolicyUtils.toMap(orgId, name, type, visibility, storeType));

        UUID id = secretDao.insert(orgId, projectId, name, owner.getId(), type, encryptedByType, storeType, visibility, insertMode);
        try {
            getSecretStore(storeType).store(id, ab);
        } catch (Exception e) {
            // we can't use the transaction here because the store may update the record in the database independently,
            // as our transaction has not yet finalized so we may end up having exception in that case
            secretDao.delete(id);
            throw new RuntimeException(e);
        }

        auditLog.add(AuditObject.SECRET, AuditAction.CREATE)
                .field("orgId", orgId)
                .field("secretId", id)
                .field("type", type)
                .field("storeType", storeType)
                .field("name", name)
                .log();

        return id;
    }

    public Collection<SecretStore> getActiveSecretStores() {
        return secretStoreProvider.getActiveSecretStores();
    }

    public List<ResourceAccessEntry> getAccessLevel(UUID orgId, String secretName) {
        assertAccess(orgId, null, secretName, ResourceAccessLevel.READER, false);
        return secretDao.getAccessLevel(orgId, secretName);
    }

    public void updateAccessLevel(UUID secretId, Collection<ResourceAccessEntry> entries, boolean isReplace) {
        assertAccess(null, secretId, null, ResourceAccessLevel.OWNER, true);

        secretDao.tx(tx -> {
            if (isReplace) {
                secretDao.deleteTeamAccess(tx, secretId);
            }

            for (ResourceAccessEntry e : entries) {
                secretDao.upsertAccessLevel(tx, secretId, e.getTeamId(), e.getLevel());
            }
        });
    }

    private byte[] getPwd(String pwd) {
        if (pwd == null) {
            return secretCfg.getServerPwd();
        }
        return pwd.getBytes(StandardCharsets.UTF_8);
    }

    private void assertProjectScope(AccessScope scope, SecretEntry e) {
        UUID projectId = e.getProjectId();
        if (projectId == null) {
            return;
        }

        // currently both the server and the agent access repositories and thus require access to secrets
        // the agent uses its own API key which is typically a "globalReader". That is why we need to check both
        // "globalReaders" and the current session token
        // TODO create a separate role or move the repository cloning into the runner and use session tokens?
        UserPrincipal u = UserPrincipal.getCurrent();
        if (u != null && Roles.isGlobalReader()) {
            return;
        }

        if (scope instanceof InternalAccessScope) {
            return;
        }

        // internal access within a scope of a project
        if (scope instanceof ProjectAccessScope) {
            UUID scopeProjectId = ((ProjectAccessScope) scope).getProjectId();
            if (!projectId.equals(scopeProjectId)) {
                throw new UnauthorizedException("Project-scoped secrets can only be accessed within the project they belong to. Secret: " + e.getName());
            }
            return;
        }

        SessionKeyPrincipal session = SessionKeyPrincipal.getCurrent();
        if (session == null) {
            throw new UnauthorizedException("Project-scoped secrets can only be accessed within a running process. Secret: " + e.getName());
        }

        ProcessEntry p = processQueueManager.get(session.getProcessKey());
        if (p == null) {
            throw new IllegalStateException("Process not found: " + session.getProcessKey());
        }

        if (!projectId.equals(p.projectId())) {
            throw new UnauthorizedException("Project-scoped secrets can only be accessed within the project they belong to. Secret: " + e.getName());
        }
    }

    private static void assertEncryptedByType(String name, SecretEncryptedByType provided, SecretEncryptedByType actual) {
        if (provided == actual) {
            return;
        }

        switch (actual) {
            case SERVER_KEY: {
                throw new SecurityException("Not a password-protected secret '" + name + "'");
            }
            case PASSWORD: {
                throw new SecurityException("The secret '" + name + "' requires a password to decrypt");
            }
            default: {
                throw new IllegalArgumentException("Unsupported secret encrypted by type: " + actual);
            }
        }
    }

    private static Secret deserialize(SecretType type, byte[] data) {
        Function<byte[], ? extends Secret> deserializer;
        switch (type) {
            case KEY_PAIR:
                deserializer = KeyPair::deserialize;
                break;
            case USERNAME_PASSWORD:
                deserializer = UsernamePassword::deserialize;
                break;
            case DATA:
                deserializer = BinaryDataSecret::new;
                break;
            default:
                throw new IllegalArgumentException("Unknown secret type: " + type);
        }
        return deserializer.apply(data);
    }

    private static SecretEncryptedByType getEncryptedBy(String pwd) {
        if (pwd == null) {
            return SecretEncryptedByType.SERVER_KEY;
        }
        return SecretEncryptedByType.PASSWORD;
    }

    private SecretStore getSecretStore(String type) {
        return secretStoreProvider.getSecretStore(type);
    }

    public String getDefaultSecretStoreType() {
        return secretStoreProvider.getDefaultStoreType();
    }

    public static class DecryptedSecret {

        private final UUID id;
        private final Secret secret;

        public DecryptedSecret(UUID id, Secret secret) {
            this.id = id;
            this.secret = secret;
        }

        public UUID getId() {
            return id;
        }

        public Secret getSecret() {
            return secret;
        }
    }

    public static class DecryptedKeyPair {

        private final UUID id;
        private final byte[] data;

        public DecryptedKeyPair(UUID id, byte[] data) { // NOSONAR
            this.id = id;
            this.data = data;
        }

        public UUID getId() {
            return id;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class DecryptedUsernamePassword {

        private final UUID id;

        public DecryptedUsernamePassword(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }
    }

    public static class DecryptedBinaryData {

        private final UUID id;

        public DecryptedBinaryData(UUID id) {
            this.id = id;
        }

        public UUID getId() {
            return id;
        }
    }

    /**
     * Scope in which access to secrets is performed. Some scopes require additional security checks.
     */
    public static abstract class AccessScope implements Serializable {

        /**
         * External access via API. Requires additional security checks.
         */
        public static AccessScope apiRequest() {
            return new ApiAccessScope();
        }

        /**
         * Internal access. The server requires a secret for some internal operations related to a
         * specific project (e.g. repository cloning).
         */
        public static AccessScope project(UUID projectId) {
            return new ProjectAccessScope(projectId);
        }

        /**
         * Generic internal access. Should be used sparingly.
         */
        public static AccessScope internal() {
            return new InternalAccessScope();
        }

        protected AccessScope() {
        }

        public abstract String getName();
    }


    private static class ApiAccessScope extends AccessScope {

        @Override
        public String getName() {
            return "apiAccess";
        }
    }

    public static class ProjectAccessScope extends AccessScope {

        private final UUID projectId;

        public ProjectAccessScope(UUID projectId) {
            super();
            this.projectId = projectId;
        }

        public UUID getProjectId() {
            return projectId;
        }

        @Override
        public String getName() {
            return "projectAccess";
        }
    }

    public static class InternalAccessScope extends AccessScope {

        @Override
        public String getName() {
            return "internal";
        }
    }
}
