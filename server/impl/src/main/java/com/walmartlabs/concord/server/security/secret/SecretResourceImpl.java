package com.walmartlabs.concord.server.security.secret;

import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.security.Permissions;
import com.walmartlabs.concord.server.api.security.secret.*;
import com.walmartlabs.concord.server.team.TeamDao;
import com.walmartlabs.concord.server.team.TeamManager;
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
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private final SecretDao secretDao;
    private final SecretManager secretManager;
    private final TeamDao teamDao;

    private final Map<String, Field<?>> key2Field;

    @Inject
    public SecretResourceImpl(SecretDao secretDao, SecretManager secretManager, TeamDao teamDao) {
        this.secretDao = secretDao;
        this.secretManager = secretManager;
        this.teamDao = teamDao;

        this.key2Field = new HashMap<>();
        key2Field.put("name", SECRETS.SECRET_NAME);
        key2Field.put("type", SECRETS.SECRET_TYPE);
    }

    private String getOrGenerateStorePassword(MultipartInput input, boolean generatePassword) {
        String password;
        try {
            password = getString(input, "storePassword");
        } catch (IOException e) {
            throw new WebApplicationException("Can't get a password from the request", e);
        }

        if (password == null && generatePassword) {
            return secretManager.generatePassword();
        }

        return password;
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    public UploadSecretResponse createOrUploadKeyPair(String name, boolean generatePassword, MultipartInput input) {
        try {
            UUID teamId = assertOptionalTeam(input);

            assertUnique(teamId, name);

            String storePassword = getOrGenerateStorePassword(input, generatePassword);

            InputStream publicIn = getStream(input, "public");
            if (publicIn != null) {
                InputStream privateIn = assertStream(input, "private");

                KeyPair k = KeyPairUtils.create(publicIn, privateIn);
                validate(k);

                secretManager.store(name, teamId, k, storePassword);
                return new UploadSecretResponse(storePassword);
            } else {
                KeyPair k = secretManager.createKeyPair(name, teamId, storePassword);
                return toPublicKey(name, k.getPublicKey(), storePassword);
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error while creating a new key pair secret", e);
        }
    }

    @Override
    public UploadSecretResponse addUsernamePassword(String name, boolean generatePassword, MultipartInput input) {
        try {
            UUID teamId = assertOptionalTeam(input);

            assertUnique(teamId, name);

            String storePassword = getOrGenerateStorePassword(input, generatePassword);

            String username = assertString(input, "username");
            String password = assertString(input, "password");
            UsernamePassword k = new UsernamePassword(username, password.toCharArray());
            secretManager.store(name, teamId, k, storePassword);

            return new UploadSecretResponse(storePassword);
        } catch (IOException e) {
            throw new WebApplicationException("Error while adding a new username/password secret", e);
        }
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    public UploadSecretResponse addPlainSecret(String name, boolean generatePassword, MultipartInput input) {
        try {
            UUID teamId = assertOptionalTeam(input);

            assertUnique(teamId, name);

            String password = getOrGenerateStorePassword(input, generatePassword);

            InputStream secret = assertStream(input, "secret");
            BinaryDataSecret k = new BinaryDataSecret(ByteStreams.toByteArray(secret));
            secretManager.store(name, teamId, k, password);

            return new UploadSecretResponse(password);
        } catch (IOException e) {
            throw new WebApplicationException("Error processing a plain secret", e);
        }
    }

    @Override
    public PublicKeyResponse createKeyPair(String name, UUID teamId, String teamName) {
        teamId = assertOptionalTeam(teamId, teamName);

        assertUnique(teamId, name);

        String password = null;
        KeyPair k = secretManager.createKeyPair(name, teamId, password);
        return toPublicKey(name, k.getPublicKey(), password);
    }

    @Override
    @RequiresPermissions(Permissions.SECRET_CREATE_NEW)
    @Validate
    @Deprecated
    public UploadSecretResponse addUsernamePassword(String name, UUID teamId, String teamName, UsernamePasswordRequest request) {
        teamId = assertOptionalTeam(teamId, teamName);

        assertUnique(teamId, name);

        UsernamePassword k = new UsernamePassword(request.getUsername(), request.getPassword());
        String password = null;
        secretManager.store(name, teamId, k, password);

        return new UploadSecretResponse(password);
    }

    @Override
    public PublicKeyResponse getPublicKey(String secretName) {
        assertPermissions(secretName, Permissions.SECRET_READ_INSTANCE,
                "The current user does not have permissions to access the specified secret");

        UUID teamId = TeamManager.DEFAULT_TEAM_ID;

        assertSecret(teamId, secretName);
        KeyPair k = secretManager.getKeyPair(teamId, secretName, null);
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

        UUID teamId = TeamManager.DEFAULT_TEAM_ID;
        UUID secretId = assertSecret(teamId, name);

        secretDao.delete(secretId);
        return new DeleteSecretResponse();
    }

    private void assertUnique(UUID teamId, String name) {
        if (secretDao.getId(teamId, name) != null) {
            throw new ValidationErrorsException("Secret already exists: " + name);
        }
    }

    private UUID assertSecret(UUID teamId, String name) {
        UUID id = secretDao.getId(teamId, name);
        if (id == null) {
            throw new ValidationErrorsException("Secret not found: " + name);
        }
        return id;
    }

    private void assertPermissions(String name, String wildcard, String message) {
        Subject subject = SecurityUtils.getSubject();
        if (!subject.isPermitted(String.format(wildcard, name))) {
            throw new UnauthorizedException(message);
        }
    }

    private UUID assertOptionalTeam(MultipartInput input) throws IOException {
        UUID teamId = null;

        String s = getString(input, "teamId");
        if (s != null) {
            teamId = UUID.fromString(s);
            if (teamDao.get(teamId) == null) {
                throw new ValidationErrorsException("Team not found: " + s);
            }
        } else {
            s = getString(input, "teamName");
            if (s != null) {
                teamId = teamDao.getId(s);
                if (teamDao.get(teamId) == null) {
                    throw new ValidationErrorsException("Team not found: " + s);
                }
            }
        }

        if (teamId == null) {
            teamId = TeamManager.DEFAULT_TEAM_ID;
        }

        return teamId;
    }

    private UUID assertOptionalTeam(UUID teamId, String teamName) {
        if (teamId != null) {
            if (teamDao.get(teamId) == null) {
                throw new ValidationErrorsException("Team not found: " + teamId);
            }
        }

        if (teamName != null) {
            teamId = teamDao.getId(teamName);
            if (teamId == null) {
                throw new ValidationErrorsException("Team not found: " + teamName);
            }
        }

        return teamId;
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

    private static void validate(KeyPair k) {
        byte[] pub = k.getPublicKey();
        byte[] priv = k.getPrivateKey();

        // with a 1024 bit key, the minimum size of a public RSA key file is 226 bytes
        if (pub == null || pub.length < 226) {
            throw new ValidationErrorsException("Invalid public key file size");
        }

        // 887 bytes is the minimum file size of a 1024 bit RSA private key
        if (priv == null || priv.length < 800) {
            throw new ValidationErrorsException("Invalid private key file size");
        }

        try {
            KeyPairUtils.validateKeyPair(pub, priv);
        } catch (Exception e) {
            throw new ValidationErrorsException("Invalid key pair data");
        }
    }
}
