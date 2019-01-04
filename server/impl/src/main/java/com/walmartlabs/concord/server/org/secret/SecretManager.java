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
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.secret.SecretDao.SecretDataEntry;
import com.walmartlabs.concord.server.org.secret.provider.SecretStoreProvider;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.Tables.SECRETS;

@Named
public class SecretManager {

    private final AuditLog auditLog;
    private final OrganizationManager orgManager;
    private final ProcessQueueDao processQueueDao;
    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;
    private final SecretStoreProvider secretStoreProvider;
    private final UserDao userDao;

    @Inject
    public SecretManager(AuditLog auditLog,
                         OrganizationManager orgManager,
                         ProcessQueueDao processQueueDao,
                         SecretDao secretDao,
                         SecretStoreConfiguration secretCfg,
                         SecretStoreProvider secretStoreProvider,
                         UserDao userDao) {

        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
        this.orgManager = orgManager;
        this.userDao = userDao;
        this.secretStoreProvider = secretStoreProvider;
        this.auditLog = auditLog;
        this.processQueueDao = processQueueDao;
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

        UserPrincipal p = UserPrincipal.assertCurrent();
        if (p.isAdmin()) {
            // an admin can access any secret
            return e;
        }

        if (level == ResourceAccessLevel.READER && (p.isGlobalReader() || p.isGlobalWriter())) {
            return e;
        } else if (level == ResourceAccessLevel.WRITER && p.isGlobalWriter()) {
            return e;
        }

        SecretOwner owner = e.getOwner();
        if (owner != null && owner.getId().equals(p.getId())) {
            // the owner can do anything with his secrets
            return e;
        }

        if (orgMembersOnly && e.getVisibility() == SecretVisibility.PUBLIC
                && level == ResourceAccessLevel.READER
                && userDao.isInOrganization(p.getId(), e.getOrgId())) {
            // organization members can access any public secret in the same organization
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
                                          SecretVisibility visibility, SecretStoreType secretStoreType) {

        orgManager.assertAccess(orgId, true);

        KeyPair k = KeyPairUtils.create();
        UUID id = store(name, orgId, projectId, k, storePassword, visibility, secretStoreType);
        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    /**
     * Stores a new SSH key pair using the provided public and private keys.
     */
    public DecryptedKeyPair createKeyPair(UUID orgId, UUID projectId, String name, String storePassword,
                                          InputStream publicKey,
                                          InputStream privateKey, SecretVisibility visibility,
                                          SecretStoreType secretStoreType) throws IOException {

        orgManager.assertAccess(orgId, true);

        KeyPair k = KeyPairUtils.create(publicKey, privateKey);
        validate(k);

        UUID id = store(name, orgId, projectId, k, storePassword, visibility, secretStoreType);
        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    /**
     * Stores a new username and password secret.
     */
    public DecryptedUsernamePassword createUsernamePassword(UUID orgId, UUID projectId, String name, String storePassword,
                                                            String username, char[] password, SecretVisibility visibility,
                                                            SecretStoreType secretStoreType) {

        orgManager.assertAccess(orgId, true);

        UsernamePassword p = new UsernamePassword(username, password);
        UUID id = store(name, orgId, projectId, p, storePassword, visibility, secretStoreType);
        return new DecryptedUsernamePassword(id);
    }

    /**
     * Stores a new single value secret.
     */
    public DecryptedBinaryData createBinaryData(UUID orgId, UUID projectId, String name, String storePassword,
                                                InputStream data, SecretVisibility visibility,
                                                SecretStoreType storeType) throws IOException {

        orgManager.assertAccess(orgId, true);

        int maxSecretDataSize = secretStoreProvider.getMaxSecretDataSize();
        InputStream limitedDataInputStream = ByteStreams.limit(data, maxSecretDataSize + 1L);
        BinaryDataSecret d = new BinaryDataSecret(ByteStreams.toByteArray(limitedDataInputStream));
        if (d.getData().length > maxSecretDataSize) {
            throw new IllegalArgumentException("File size exceeds limit of " + maxSecretDataSize + " bytes");
        }
        UUID id = store(name, orgId, projectId, d, storePassword, visibility, storeType);
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
     * Updates name and/or visibility of an existing secret.
     */
    public void update(UUID secretId, String newName, SecretVisibility visibility) {
        SecretEntry e = assertAccess(null, secretId, newName, ResourceAccessLevel.WRITER, true);
        secretDao.update(e.getId(), newName, visibility);
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
                .field("id", e.getId())
                .field("orgId", e.getId())
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

        byte[] data = getSecretStore(e.getStoreType()).get(e.getId());
        if (data == null) {
            throw new IllegalStateException("Can't find the secret's data in the store " + e.getStoreType() + " : " + e.getId());
        }

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        byte[] ab = SecretUtils.decrypt(data, pwd, salt);

        auditLog.add(AuditObject.SECRET, AuditAction.ACCESS)
                .field("id", e.getId())
                .field("orgId", e.getOrgId())
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
    public List<SecretEntry> list(UUID orgId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        if (p.isAdmin() || p.isGlobalReader() || p.isGlobalWriter()) {
            userId = null;
        }

        return secretDao.list(orgId, userId, SECRETS.SECRET_NAME, true);
    }

    /**
     * Updates a secret's access level for the specified team.
     */
    public void updateAccessLevel(UUID secretId, UUID teamId, ResourceAccessLevel level) {
        assertAccess(null, secretId, null, ResourceAccessLevel.OWNER, true);
        secretDao.upsertAccessLevel(secretId, teamId, level);
    }

    private UUID store(String name, UUID orgId, UUID projectId, Secret s, String password, SecretVisibility visibility, SecretStoreType storeType) {
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

        UUID id = secretDao.insert(orgId, projectId, name, getOwnerId(), type, encryptedByType, storeType, visibility);
        try {
            getSecretStore(storeType).store(id, ab);
        } catch (Exception e) {
            // we can't use the transaction here because the store may update the record in the database independently,
            // as our transaction has not yet finalized so we may end up having exception in that case
            secretDao.delete(id);
            throw new RuntimeException(e);
        }

        auditLog.add(AuditObject.SECRET, AuditAction.CREATE)
                .field("id", id)
                .field("orgId", orgId)
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
        if (u != null && u.isGlobalReader()) {
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

        ProcessEntry p = processQueueDao.get(session.getProcessKey());
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
                throw new SecurityException("Not a password-protected secret: " + name);
            }
            case PASSWORD: {
                throw new SecurityException("The secret requires a password to decrypt: " + name);
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

    private static void validate(KeyPair k) {
        byte[] pub = k.getPublicKey();
        byte[] priv = k.getPrivateKey();

        // with a 1024 bit key, the minimum size of a public RSA key file is 226 bytes
        if (pub == null || pub.length < 226) {
            throw new IllegalArgumentException("Invalid public key file size");
        }

        // 887 bytes is the minimum file size of a 1024 bit RSA private key
        if (priv == null || priv.length < 800) {
            throw new IllegalArgumentException("Invalid private key file size");
        }

        try {
            KeyPairUtils.validateKeyPair(pub, priv);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid key pair data", e);
        }
    }

    private static SecretEncryptedByType getEncryptedBy(String pwd) {
        if (pwd == null) {
            return SecretEncryptedByType.SERVER_KEY;
        }
        return SecretEncryptedByType.PASSWORD;
    }

    private static UUID getOwnerId() {
        UserPrincipal p = UserPrincipal.assertCurrent();
        return p.getId();
    }

    private SecretStore getSecretStore(SecretStoreType type) {
        return secretStoreProvider.getSecretStore(type);
    }

    public SecretStoreType getDefaultSecretStoreType() {
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
