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


import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.validation.ConcordKey;
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
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedBinaryData;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedKeyPair;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedSecret;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedUsernamePassword;
import com.walmartlabs.concord.server.org.team.TeamDao;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private final OrganizationManager orgManager;
    private final OrganizationDao orgDao;
    private final SecretManager secretManager;
    private final SecretDao secretDao;
    private final TeamDao teamDao;

    @Inject
    public SecretResourceImpl(OrganizationManager orgManager,
                              OrganizationDao orgDao,
                              SecretManager secretManager,
                              SecretDao secretDao,
                              TeamDao teamDao) {

        this.orgManager = orgManager;
        this.orgDao = orgDao;
        this.secretManager = secretManager;
        this.secretDao = secretDao;
        this.teamDao = teamDao;
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

            boolean generatePwd = MultipartUtils.getBoolean(input, "generatePassword", false);
            String storePwd = getOrGenerateStorePassword(input, generatePwd);
            SecretVisibility visibility = getVisibility(input);

            switch (type) {
                case KEY_PAIR: {
                    return createKeyPair(org.getId(), name, storePwd, visibility, input, storeType);
                }
                case USERNAME_PASSWORD: {
                    return createUsernamePassword(org.getId(), name, storePwd, visibility, input, storeType);
                }
                case DATA: {
                    return createData(org.getId(), name, storePwd, visibility, input, storeType);
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
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        String storePwd = MultipartUtils.getString(input, "storePassword");

        DecryptedSecret s = secretManager.getSecret(org.getId(), secretName, storePwd, SecretType.DATA);
        BinaryDataSecret d = (BinaryDataSecret) s.getSecret();
        return Response.ok(d.getData()).build();
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

    private PublicKeyResponse createKeyPair(UUID orgId, String name, String storePassword, SecretVisibility visibility, MultipartInput input, SecretStoreType storeType) throws IOException {
        DecryptedKeyPair k;

        InputStream publicKey = MultipartUtils.getStream(input, "public");
        if (publicKey != null) {
            InputStream privateKey = assertStream(input, "private");
            try {
                k = secretManager.createKeyPair(orgId, name, storePassword, publicKey, privateKey, visibility, storeType);
            } catch (IllegalArgumentException e) {
                throw new ValidationErrorsException(e.getMessage());
            }
        } else {
            k = secretManager.createKeyPair(orgId, name, storePassword, visibility, storeType);
        }

        return new PublicKeyResponse(k.getId(), OperationResult.CREATED, storePassword, new String(k.getData()));
    }

    private SecretOperationResponse createUsernamePassword(UUID orgId, String name, String storePassword,
                                                           SecretVisibility visibility, MultipartInput input,
                                                           SecretStoreType storeType) {

        String username = assertString(input, "username");
        String password = assertString(input, "password");

        DecryptedUsernamePassword e = secretManager.createUsernamePassword(orgId, name, storePassword, username, password.toCharArray(), visibility, storeType);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }

    private SecretOperationResponse createData(UUID orgId, String name, String storePassword,
                                               SecretVisibility visibility, MultipartInput input,
                                               SecretStoreType storeType) throws IOException {

        InputStream data = assertStream(input, "data");
        DecryptedBinaryData e = secretManager.createBinaryData(orgId, name, storePassword, data, visibility, storeType);
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
            password = MultipartUtils.getString(input, "storePassword");
        } catch (WebApplicationException e) {
            throw new WebApplicationException("Can't get a password from the request", e);
        }

        if (password == null && generatePassword) {
            return secretManager.generatePassword();
        }

        return password;
    }

    private String assertName(MultipartInput input) throws IOException {
        String s = assertString(input, "name");
        if (s == null || s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }

        if (!s.matches(ConcordKey.PATTERN)) {
            throw new ValidationErrorsException("Invalid secret name: " + s);
        }

        return s;
    }

    private static SecretType assertType(MultipartInput input) {
        String s = MultipartUtils.getString(input, "type");
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
        String s = MultipartUtils.getString(input, "storeType");
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
        String s = MultipartUtils.getString(input, "visibility");
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
}
