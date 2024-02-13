package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.OperationResult;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

public final class SecretResourceUtils {

    public static PublicKeyResponse createKeyPair(SecretManager secretManager, UUID orgId, Set<UUID> projectIds, String name, String storePassword, SecretVisibility visibility, MultipartInput input, String storeType) throws IOException {
        SecretManager.DecryptedKeyPair k;

        InputStream publicKey = MultipartUtils.getStream(input, Constants.Multipart.PUBLIC);
        if (publicKey != null) {
            InputStream privateKey = MultipartUtils.assertStream(input, Constants.Multipart.PRIVATE);
            try {
                k = secretManager.createKeyPair(orgId, projectIds, name, storePassword, publicKey, privateKey, visibility, storeType);
            } catch (IllegalArgumentException e) {
                throw new ValidationErrorsException(e.getMessage());
            }
        } else {
            k = secretManager.createKeyPair(orgId, projectIds, name, storePassword, visibility, storeType);
        }

        return new PublicKeyResponse(k.getId(), OperationResult.CREATED, storePassword, new String(k.getData()));
    }

    public static SecretOperationResponse createUsernamePassword(SecretManager secretManager, UUID orgId,  Set<UUID> projectIds, String name, String storePassword,
                                                           SecretVisibility visibility, MultipartInput input,
                                                           String storeType) {

        String username = MultipartUtils.assertString(input, Constants.Multipart.USERNAME);
        String password = MultipartUtils.assertString(input, Constants.Multipart.PASSWORD);

        SecretManager.DecryptedUsernamePassword e = secretManager.createUsernamePassword(orgId, projectIds, name, storePassword, username, password.toCharArray(), visibility, storeType);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }

    public static SecretOperationResponse createData(SecretManager secretManager, UUID orgId,  Set<UUID> projectIds, String name, String storePassword,
                                               SecretVisibility visibility, MultipartInput input,
                                               String storeType) throws IOException {

        InputStream data = MultipartUtils.assertStream(input, Constants.Multipart.DATA);
        SecretManager.DecryptedBinaryData e = secretManager.createBinaryData(orgId, projectIds, name, storePassword, data, visibility, storeType);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED, storePassword);
    }



    public static String assertName(MultipartInput input) {
        String s = MultipartUtils.assertString(input, Constants.Multipart.NAME);
        if (s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }

        if (!s.matches(ConcordKey.PATTERN)) {
            throw new ValidationErrorsException("Invalid secret name: " + s + ". " + ConcordKey.MESSAGE);
        }

        return s;
    }

    public static SecretType assertType(MultipartInput input) {
        SecretType type = getType(input);
        if (type == null) {
            throw new ValidationErrorsException("'type' is required");
        }
        return type;
    }

    public static SecretType getType(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.TYPE);
        if (s == null) {
            return null;
        }

        try {
            return SecretType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Unsupported secret type: " + s);
        }
    }

    public static String assertStoreType(SecretManager secretManager, MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.STORE_TYPE);
        if (s == null) {
            return secretManager.getDefaultSecretStoreType();
        }

        // check if the given secret source type is enabled or not
        boolean isStoreActive = secretManager.getActiveSecretStores().stream()
                .anyMatch(store -> store.getType().equalsIgnoreCase(s));

        if (!isStoreActive) {
            throw new ValidationErrorsException("Secret store of type " + s + " is not available!");
        }

        return s;
    }

    public static SecretVisibility getVisibility(MultipartInput input) {
        String s = MultipartUtils.getString(input, Constants.Multipart.VISIBILITY);
        if (s == null) {
            return SecretVisibility.PUBLIC;
        }

        try {
            return SecretVisibility.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new ConcordApplicationException("Invalid visibility value: " + s, Response.Status.BAD_REQUEST);
        }
    }

    public static String getOrGenerateStorePassword(MultipartInput input, boolean generatePassword) {
        String password;
        try {
            password = MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD);
        } catch (WebApplicationException e) {
            throw new ConcordApplicationException("Can't get a password from the request", e);
        }

        if (password != null) {
            try {
                PasswordChecker.check(password);
            } catch (PasswordChecker.CheckerException e) {
                throw new ConcordApplicationException("Invalid password: " + e.getMessage(), Response.Status.BAD_REQUEST);
            }
        }

        if (password == null && generatePassword) {
            return PasswordGenerator.generate();
        }

        return password;
    }

    public static void assertUnique(SecretDao secretDao, UUID orgId, String name) {
        if (secretDao.getId(orgId, name) != null) {
            throw new ValidationErrorsException("Secret already exists: " + name);
        }
    }

}
