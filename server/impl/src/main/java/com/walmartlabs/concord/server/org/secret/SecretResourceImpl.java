package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.GenericOperationResultResponse;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.org.OrganizationEntry;
import com.walmartlabs.concord.server.api.org.ResourceAccessEntry;
import com.walmartlabs.concord.server.api.org.secret.*;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedBinaryData;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedKeyPair;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedUsernamePassword;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private final OrganizationManager orgManager;
    private final SecretManager secretManager;
    private final SecretDao secretDao;

    @Inject
    public SecretResourceImpl(OrganizationManager orgManager, SecretManager secretManager, SecretDao secretDao) {
        this.orgManager = orgManager;
        this.secretManager = secretManager;
        this.secretDao = secretDao;
    }

    @Override
    @Validate
    public SecretOperationResponse create(String orgName, MultipartInput input) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        try {
            SecretType type = assertType(input);

            String name = assertName(input);
            assertUnique(org.getId(), name);

            boolean generatePwd = MultipartUtils.getBoolean(input, "generatePassword", false);
            String storePwd = getOrGenerateStorePassword(input, generatePwd);
            SecretVisibility visibility = getVisibility(input);

            switch (type) {
                case KEY_PAIR: {
                    return createKeyPair(org.getId(), name, storePwd, visibility, input);
                }
                case USERNAME_PASSWORD: {
                    return createUsernamePassword(org.getId(), name, storePwd, visibility, input);
                }
                case DATA: {
                    return createData(org.getId(), name, storePwd, visibility, input);
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
    public DeleteSecretResponse delete(String orgName, String secretName) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);
        secretManager.delete(org.getId(), secretName);
        return new DeleteSecretResponse();
    }

    @Override
    @Validate
    public GenericOperationResultResponse updateAccessLevel(String orgName, String secretName, ResourceAccessEntry entry) {
        OrganizationEntry org = orgManager.assertAccess(orgName, true);

        UUID secretId = secretDao.getId(org.getId(), secretName);
        if (secretId == null) {
            throw new WebApplicationException("Secret not found: " + secretName, Status.NOT_FOUND);
        }

        secretManager.updateAccessLevel(secretId, entry.getTeamId(), entry.getLevel());
        return new GenericOperationResultResponse(OperationResult.UPDATED);
    }

    private PublicKeyResponse createKeyPair(UUID orgId, String name, String storePassword, SecretVisibility visibility, MultipartInput input) throws IOException {
        DecryptedKeyPair k;

        InputStream publicKey = MultipartUtils.getStream(input, "public");
        if (publicKey != null) {
            InputStream privateKey = assertStream(input, "private");
            try {
                k = secretManager.createKeyPair(orgId, name, storePassword, publicKey, privateKey, visibility);
            } catch (IllegalArgumentException e) {
                throw new ValidationErrorsException(e.getMessage());
            }
        } else {
            k = secretManager.createKeyPair(orgId, name, storePassword, visibility);
        }

        return new PublicKeyResponse(k.getId(), OperationResult.CREATED, storePassword, new String(k.getData()));
    }

    private SecretOperationResponse createUsernamePassword(UUID orgId, String name, String storePassword,
                                                           SecretVisibility visibility, MultipartInput input) {

        String username = assertString(input, "username");
        String password = assertString(input, "password");

        DecryptedUsernamePassword e = secretManager.createUsernamePassword(orgId, name, storePassword, username, password.toCharArray(), visibility);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }

    private SecretOperationResponse createData(UUID orgId, String name, String storePassword,
                                               SecretVisibility visibility, MultipartInput input) throws IOException {

        InputStream data = assertStream(input, "data");
        DecryptedBinaryData e = secretManager.createBinaryData(orgId, name, storePassword, data, visibility);
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
