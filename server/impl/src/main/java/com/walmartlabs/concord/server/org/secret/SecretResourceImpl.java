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

import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.GenericOperationResult;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.api.org.secret.*;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.ResourceAccessUtils;
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedBinaryData;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedKeyPair;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedUsernamePassword;
import com.walmartlabs.concord.server.org.team.TeamDao;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private static final Logger log = LoggerFactory.getLogger(SecretResourceImpl.class);

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final SecretManager secretManager;
    private final SecretDao secretDao;
    private final TeamDao teamDao;
    private final ProjectDao projectDao;

    @Inject
    public SecretResourceImpl(OrganizationManager orgManager,
                              OrganizationDao orgDao,
                              SecretManager secretManager,
                              SecretDao secretDao,
                              TeamDao teamDao,
                              ProjectDao projectDao) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.secretManager = secretManager;
        this.secretDao = secretDao;
        this.teamDao = teamDao;
        this.projectDao = projectDao;
    }

    @Override
    @Validate
    public SecretOperationResponse create(String orgName, MultipartInput input) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        try {
            SecretType type = assertType(input);
            SecretStoreType storeType = assertStoreType(input);

            String name = assertName(input);
            assertUnique(org.getId(), name);

            boolean generatePwd = MultipartUtils.getBoolean(input, Constants.Multipart.GENERATE_PASSWORD, false);
            String storePwd = getOrGenerateStorePassword(input, generatePwd);
            SecretVisibility visibility = getVisibility(input);

            UUID projectId = getProject(input, org.getId());

            switch (type) {
                case KEY_PAIR: {
                    return createKeyPair(org.getId(), projectId, name, storePwd, visibility, input, storeType);
                }
                case USERNAME_PASSWORD: {
                    return createUsernamePassword(org.getId(), projectId, name, storePwd, visibility, input, storeType);
                }
                case DATA: {
                    return createData(org.getId(), projectId, name, storePwd, visibility, input, storeType);
                }
                default:
                    throw new ValidationErrorsException("Unsupported secret type: " + type);
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error while processing the request: " + e.getMessage(), e);
        }
    }

    @Override
    @Validate
    public GenericOperationResult update(String orgName, String secretName, SecretUpdateRequest req) {
        if (req.getName() == null && req.getVisibility() == null) {
            throw new ValidationErrorsException("Nothing to update");
        }

        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        secretManager.update(org.getId(), secretName, req.getName(), req.getVisibility());

        return new GenericOperationResult(OperationResult.UPDATED);
    }

    @Override
    public SecretEntry get(String orgName, String secretName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretManager.assertAccess(org.getId(), null, secretName, ResourceAccessLevel.READER, false);
    }

    @Override
    public Response getData(String orgName, String secretName, MultipartInput input) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        String password = MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD);

        SecretDao.SecretDataEntry entry;
        try {
            entry = secretManager.getRaw(org.getId(), secretName, password);
            if (entry == null) {
                return null;
            }
        } catch (SecurityException e) {
            log.warn("fetchSecret -> error: {}", e.getMessage());
            throw new SecretException("Error while fetching a secret: " + e.getMessage());
        } catch (ValidationErrorsException e) {
            log.warn("fetchSecret -> error: {}", e.getMessage());
            return null;
        }

        try {
            return Response.ok((StreamingOutput) output -> output.write(entry.getData()),
                    MediaType.APPLICATION_OCTET_STREAM)
                    .header(InternalConstants.Headers.SECRET_TYPE, entry.getType().name())
                    .build();
        } catch (Exception e) {
            log.error("fetchSecret ['{}'] -> error while fetching a secret", secretName, e);
            throw new WebApplicationException("Error while fetching a secret: " + e.getMessage());
        }
    }

    @Override
    @Validate
    public PublicKeyResponse getPublicKey(String orgName, String secretName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        DecryptedKeyPair k = secretManager.getKeyPair(org.getId(), secretName);
        return new PublicKeyResponse(k.getId(), null, null, new String(k.getData()));
    }

    @Override
    @Validate
    public List<SecretEntry> list(String orgName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, false);
        return secretDao.list(org.getId(), SECRETS.SECRET_NAME, true);
    }

    @Override
    @Validate
    public GenericOperationResult delete(String orgName, String secretName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        secretManager.delete(org.getId(), secretName);
        return new GenericOperationResult(OperationResult.DELETED);
    }

    @Override
    @Validate
    public GenericOperationResult updateAccessLevel(String orgName, String secretName, ResourceAccessEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID secretId = secretDao.getId(org.getId(), secretName);
        if (secretId == null) {
            throw new WebApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
        }

        UUID teamId = ResourceAccessUtils.getTeamId(orgDao, teamDao, org.getId(), entry);

        secretManager.updateAccessLevel(secretId, teamId, entry.getLevel());
        return new GenericOperationResult(OperationResult.UPDATED);
    }

    private PublicKeyResponse createKeyPair(UUID orgId, UUID projectId, String name, String storePassword, SecretVisibility visibility, MultipartInput input, SecretStoreType storeType) throws IOException {
        DecryptedKeyPair k;

        InputStream publicKey = MultipartUtils.getStream(input, Constants.Multipart.PUBLIC);
        if (publicKey != null) {
            InputStream privateKey = assertStream(input, Constants.Multipart.PRIVATE);
            try {
                k = secretManager.createKeyPair(orgId, projectId, name, storePassword, publicKey, privateKey, visibility, storeType);
            } catch (IllegalArgumentException e) {
                throw new ValidationErrorsException(e.getMessage());
            }
        } else {
            k = secretManager.createKeyPair(orgId, projectId, name, storePassword, visibility, storeType);
        }

        return new PublicKeyResponse(k.getId(), OperationResult.CREATED, storePassword, new String(k.getData()));
    }

    private SecretOperationResponse createUsernamePassword(UUID orgId, UUID projectId, String name, String storePassword,
                                                           SecretVisibility visibility, MultipartInput input,
                                                           SecretStoreType storeType) {

        String username = assertString(input, Constants.Multipart.USERNAME);
        String password = assertString(input, Constants.Multipart.PASSWORD);

        DecryptedUsernamePassword e = secretManager.createUsernamePassword(orgId, projectId, name, storePassword, username, password.toCharArray(), visibility, storeType);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }

    private SecretOperationResponse createData(UUID orgId, UUID projectId, String name, String storePassword,
                                               SecretVisibility visibility, MultipartInput input,
                                               SecretStoreType storeType) throws IOException {

        InputStream data = assertStream(input, Constants.Multipart.DATA);
        DecryptedBinaryData e = secretManager.createBinaryData(orgId, projectId, name, storePassword, data, visibility, storeType);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }

    private void assertUnique(UUID orgId, String name) {
        if (secretDao.getId(orgId, name) != null) {
            throw new ValidationErrorsException("Secret already exists: " + name);
        }
    }

    private String getOrGenerateStorePassword(MultipartInput input, boolean generatePassword) {
        String password;
        try {
            password = MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD);
        } catch (WebApplicationException e) {
            throw new WebApplicationException("Can't get a password from the request", e);
        }

        if (password == null && generatePassword) {
            return secretManager.generatePassword();
        }

        return password;
    }

    private String assertName(MultipartInput input) {
        String s = assertString(input, Constants.Multipart.NAME);
        if (s == null || s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }

        if (!s.matches(ConcordKey.PATTERN)) {
            throw new ValidationErrorsException("Invalid secret name: " + s);
        }

        return s;
    }

    private static SecretType assertType(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.TYPE);
        if (s == null) {
            throw new ValidationErrorsException("'type' is required");
        }

        try {
            return SecretType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Unsupported secret type: " + s);
        }
    }

    private SecretStoreType assertStoreType(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.STORE_TYPE);
        if (s == null) {
            return secretManager.getDefaultSecretStoreType();
        }

        SecretStoreType t;
        try {
            t = SecretStoreType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Unsupported secret store type: " + s);
        }

        // check if the given secret source type is enabled or not
        boolean isStoreActive = secretManager.getActiveSecretStores().stream()
                .anyMatch(store -> store.getType() == t);

        if (!isStoreActive) {
            throw new ValidationErrorsException("Secret store of type " + t + " is not available!");
        }

        return t;
    }

    private static String assertString(MultipartInput input, String key) {
        String s = MultipartUtils.getString(input, key);
        if (s == null) {
            throw new ValidationErrorsException("Value not found: " + key);
        }
        return s;
    }

    private static SecretVisibility getVisibility(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.VISIBILITY);
        if (s == null) {
            return SecretVisibility.PUBLIC;
        }

        try {
            return SecretVisibility.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException("Invalid visibility value: " + s, Status.BAD_REQUEST);
        }
    }

    private static InputStream assertStream(MultipartInput input, String key) {
        InputStream in = MultipartUtils.getStream(input, key);
        if (in == null) {
            throw new ValidationErrorsException("Value not found: " + key);
        }
        return in;
    }

    private UUID getProject(MultipartInput input, UUID orgId) {
        UUID id = MultipartUtils.getUuid(input, Constants.Multipart.PROJECT_ID);
        String name = MultipartUtils.getString(input, Constants.Multipart.PROJECT_NAME);
        if (id == null && name != null) {
            id = projectDao.getId(orgId, name);
            if (id == null) {
                throw new ValidationErrorsException("Project not found: " + name);
            }
        }
        return id;
    }
}
