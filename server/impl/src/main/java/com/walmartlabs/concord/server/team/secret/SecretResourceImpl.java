package com.walmartlabs.concord.server.team.secret;

import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.OperationResult;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.api.team.TeamEntry;
import com.walmartlabs.concord.server.api.team.TeamRole;
import com.walmartlabs.concord.server.api.team.secret.PublicKeyResponse;
import com.walmartlabs.concord.server.api.team.secret.SecretOperationResponse;
import com.walmartlabs.concord.server.api.team.secret.SecretResource;
import com.walmartlabs.concord.server.security.secret.SecretManager;
import com.walmartlabs.concord.server.security.secret.SecretManager.BinaryDataEntry;
import com.walmartlabs.concord.server.security.secret.SecretManager.KeyPairEntry;
import com.walmartlabs.concord.server.security.secret.SecretManager.UsernamePasswordEntry;
import com.walmartlabs.concord.server.team.TeamManager;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.ValidationErrorsException;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Named
public class SecretResourceImpl implements SecretResource, Resource {

    private final TeamManager teamManager;
    private final SecretManager secretManager;

    @Inject
    public SecretResourceImpl(TeamManager teamManager, SecretManager secretManager) {
        this.teamManager = teamManager;
        this.secretManager = secretManager;
    }

    @Override
    public SecretOperationResponse create(String teamName, MultipartInput input) {
        UUID teamId = assertTeam(teamName);

        try {
            SecretType type = assertType(input);

            String name = assertName(input);
            assertUnique(teamId, name);

            boolean generatePwd = getBoolean(input, "generatePassword", false);
            String storePwd = getOrGenerateStorePassword(input, generatePwd);

            switch (type) {
                case KEY_PAIR: {
                    return createKeyPair(teamId, name, storePwd, input);
                }
                case USERNAME_PASSWORD: {
                    return createUsernamePassword(teamId, name, storePwd, input);
                }
                case DATA: {
                    return createData(teamId, name, storePwd, input);
                }
                default:
                    throw new ValidationErrorsException("Unsupported secret type: " + type);
            }
        } catch (IOException e) {
            throw new WebApplicationException("Error while processing the request: " + e.getMessage(), e);
        }
    }

    private PublicKeyResponse createKeyPair(UUID teamId, String name, String storePassword, MultipartInput input) throws IOException {
        KeyPairEntry e;

        InputStream publicKey = getStream(input, "public");
        if (publicKey != null) {
            InputStream privateKey = assertStream(input, "private");
            e = secretManager.createKeyPair(teamId, name, storePassword, publicKey, privateKey);
        } else {
            e = secretManager.createKeyPair(teamId, name, storePassword);
        }

        return new PublicKeyResponse(e.getId(), OperationResult.CREATED, new String(e.getData()));
    }

    private SecretOperationResponse createUsernamePassword(UUID teamId, String name, String storePassword, MultipartInput input) throws IOException {
        String username = assertString(input, "username");
        String password = assertString(input, "password");

        UsernamePasswordEntry e = secretManager.createUsernamePassword(teamId, name, storePassword, username, password.toCharArray());
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED);
    }

    private SecretOperationResponse createData(UUID teamId, String name, String storePassword, MultipartInput input) throws IOException {
        InputStream data = assertStream(input, "data");
        BinaryDataEntry e = secretManager.createBinaryData(teamId, name, storePassword, data);
        return new SecretOperationResponse(e.getId(), OperationResult.CREATED);
    }

    private UUID assertTeam(String teamName) {
        TeamEntry t = teamManager.assertTeamAccess(teamName, TeamRole.WRITER, true);
        return t.getId();
    }

    private void assertUnique(UUID teamId, String name) {
        if (secretManager.exists(teamId, name)) {
            throw new ValidationErrorsException("Secret already exists: " + name);
        }
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

    private String assertName(MultipartInput input) throws IOException {
        String s = assertString(input, "name");
        if (s == null || s.trim().isEmpty()) {
            throw new ValidationErrorsException("'name' is required");
        }
        return s;
    }

    private static SecretType assertType(MultipartInput input) throws IOException {
        String s = getString(input, "type");
        if (s == null) {
            throw new ValidationErrorsException("'type' is required");
        }

        try {
            return SecretType.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationErrorsException("Unsupported secret type: " + s);
        }
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

    private static InputStream assertStream(MultipartInput input, String key) throws IOException {
        InputStream in = getStream(input, key);
        if (in == null) {
            throw new ValidationErrorsException("Value not found: " + key);
        }
        return in;
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

    private static boolean getBoolean(MultipartInput input, String key, boolean defaultValue) throws IOException {
        String s = getString(input, key);
        if (s == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(s);
    }
}
