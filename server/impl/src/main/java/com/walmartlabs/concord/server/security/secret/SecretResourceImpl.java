package com.walmartlabs.concord.server.security.secret;

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.secret.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.UnauthorizedException;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.apache.shiro.subject.Subject;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jooq.Field;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private static final int SECRET_PASSWORD_LENGTH = 12;
    private static final String SECRET_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}|,<.>/?\\";

    private final SecretDao secretDao;
    private final SecretManager secretManager;

    private final Map<String, Field<?>> key2Field;

    @Inject
    public SecretResourceImpl(SecretDao secretDao, SecretManager secretManager) {
        this.secretDao = secretDao;
        this.secretManager = secretManager;

        this.key2Field = new HashMap<>();
        key2Field.put("name", SECRETS.SECRET_NAME);
        key2Field.put("type", SECRETS.SECRET_TYPE);
    }

    private String generatePassword() {
        return RandomStringUtils.random(SECRET_PASSWORD_LENGTH, SECRET_PASSWORD_CHARS);
    }

    private String getOrGenerateStorePassword(MultipartInput input, boolean generatePassword) {
        String password;
        try {
            password = getString(input, "storePassword");
        } catch (IOException e) {
            throw new WebApplicationException("Can't get a password from the request", e);
        }

        if (password == null && generatePassword) {
            return generatePassword();
        }

        return password;
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    public UploadSecretResponse createOrUploadKeyPair(String name, boolean generatePassword, MultipartInput input) {
        assertUnique(name);

        String storePassword = getOrGenerateStorePassword(input, generatePassword);

        try {
            InputStream publicIn = getStream(input, "public");
            if (publicIn != null) {
                InputStream privateIn = assertStream(input, "private");
                KeyPair k = KeyPairUtils.create(publicIn, privateIn);
                secretManager.store(name, k, storePassword);
                return new UploadSecretResponse(storePassword);
            } else {
                KeyPair k = secretManager.createKeyPair(name, storePassword);
                return toPublicKey(name, k.getPublicKey(), storePassword);
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error while creating a new key pair secret", e);
        }
    }

    @Override
    public UploadSecretResponse addUsernamePassword(String name, boolean generatePassword, MultipartInput input) {
        assertUnique(name);

        String storePassword = getOrGenerateStorePassword(input, generatePassword);

        UsernamePassword k;
        try {
            String username = assertString(input, "username");
            String password = assertString(input, "password");
            k = new UsernamePassword(username, password.toCharArray());
        } catch (IOException e) {
            throw new WebApplicationException("Error while adding a new username/password secret", e);
        }
        secretManager.store(name, k, storePassword);

        return new UploadSecretResponse(storePassword);
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    public UploadSecretResponse addPlainSecret(String name, boolean generatePassword, MultipartInput input) {
        assertUnique(name);

        String password = getOrGenerateStorePassword(input, generatePassword);

        BinaryDataSecret k;
        try {
            InputStream secret = assertStream(input, "secret");
            k = new BinaryDataSecret(ByteStreams.toByteArray(secret));
        } catch (IOException e) {
            throw new WebApplicationException("Error processing a plain secret", e);
        }
        secretManager.store(name, k, password);

        return new UploadSecretResponse(password);
    }

    @Override
    public PublicKeyResponse createKeyPair(String name) {
        assertUnique(name);

        String password = null;
        KeyPair k = secretManager.createKeyPair(name, password);
        return toPublicKey(name, k.getPublicKey(), password);
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    @Deprecated
    public UploadSecretResponse addUsernamePassword(String name, UsernamePasswordRequest request) {
        assertUnique(name);

        UsernamePassword k = new UsernamePassword(request.getUsername(), request.getPassword());
        String password = null;
        secretManager.store(name, k, password);

        return new UploadSecretResponse(password);
    }

    @Override
    public PublicKeyResponse getPublicKey(String secretName) {
        assertPermissions(secretName, Permissions.SECRET_READ_INSTANCE,
                "The current user does not have permissions to access the specified secret");

        assertSecret(secretName);
        KeyPair k = secretManager.getKeyPair(secretName, null);
        return toPublicKey(secretName, k.getPublicKey(), null);
    }

    @Override
    public List<SecretEntry> list(String sortBy, boolean asc) {
        Field<?> sortField = key2Field.get(sortBy);
        if (sortField == null) {
            throw new ValidationErrorsException("Unknown sort field: " + sortBy);
        }
        return secretDao.list(sortField, asc);
    }

    @Override
    @Validate
    public DeleteSecretResponse delete(String name) {
        assertPermissions(name, Permissions.SECRET_DELETE_INSTANCE,
                "The current user does not have permissions to delete the specified secret");

        assertSecret(name);

        secretDao.delete(name);
        return new DeleteSecretResponse();
    }

    private void assertUnique(String name) {
        if (secretDao.exists(name)) {
            throw new ValidationErrorsException("Secret already exists: " + name);
        }
    }

    private void assertSecret(String name) {
        if (!secretDao.exists(name)) {
            throw new ValidationErrorsException("Secret not found: " + name);
        }
    }

    private void assertPermissions(String name, String wildcard, String message) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(wildcard, name))) {
            throw new UnauthorizedException(message);
        }
    }

    private static PublicKeyResponse toPublicKey(String name, byte[] ab, String password) {
        String s = new String(ab).trim();
        return new PublicKeyResponse(name, s, password);
    }

    private static InputStream assertStream(MultipartInput input, String key) throws IOException {
        InputStream in = getStream(input, key);
        if (in == null) {
            throw new ValidationErrorsException("Value not found: " + key);
        }
        return in;
    }

    private static String assertString(MultipartInput input, String key) throws IOException {
        String s = getString(input, key);
        if (s == null) {
            throw new ValidationErrorsException("Value not found: " + key);
        }
        return s;
    }

    private static String getString(MultipartInput input, String key) throws IOException {
        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (key.equals(name)) {
                return p.getBodyAsString();
            }
        }
        return null;
    }

    private static InputStream getStream(MultipartInput input, String key) throws IOException {
        for (InputPart p : input.getParts()) {
            String name = MultipartUtils.extractName(p);
            if (key.equals(name)) {
                return p.getBody(InputStream.class, null);
            }
        }
        return null;
    }
}
