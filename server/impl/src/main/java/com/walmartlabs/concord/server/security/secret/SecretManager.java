package com.walmartlabs.concord.server.security.secret;

import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.SecretStoreType;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.api.security.secret.SecretEntry;
import com.walmartlabs.concord.server.api.security.secret.SecretType;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.security.secret.SecretDao.SecretDataEntry;
import org.apache.commons.lang3.RandomStringUtils;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static com.walmartlabs.concord.server.jooq.tables.Secrets.SECRETS;

@Named
public class SecretManager {

    private static final int SECRET_PASSWORD_LENGTH = 12;
    private static final String SECRET_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~`!@#$%^&*()-_=+[{]}|,<.>/?\\";

    private final SecretDao secretDao;
    private final SecretStoreConfiguration secretCfg;

    @Inject
    public SecretManager(SecretDao secretDao,
                         SecretStoreConfiguration secretCfg) {

        this.secretDao = secretDao;
        this.secretCfg = secretCfg;
    }

    public DecryptedKeyPair createKeyPair(UUID teamId, String name, String storePassword) throws IOException {
        KeyPair k = KeyPairUtils.create();
        UUID id = store(name, teamId, k, storePassword);
        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    public DecryptedKeyPair createKeyPair(UUID teamId, String name, String storePassword, InputStream publicKey, InputStream privateKey) throws IOException {
        KeyPair k = KeyPairUtils.create(publicKey, privateKey);
        validate(k);

        UUID id = store(name, teamId, k, storePassword);
        return new DecryptedKeyPair(id, k.getPublicKey());
    }

    public DecryptedUsernamePassword createUsernamePassword(UUID teamId, String name, String storePassword, String username, char[] password) {
        UsernamePassword p = new UsernamePassword(username, password);
        UUID id = store(name, teamId, p, storePassword);
        return new DecryptedUsernamePassword(id);
    }

    public DecryptedBinaryData createBinaryData(UUID teamId, String name, String storePassword, InputStream data) throws IOException {
        BinaryDataSecret d = new BinaryDataSecret(ByteStreams.toByteArray(data));
        UUID id = store(name, teamId, d, storePassword);
        return new DecryptedBinaryData(id);
    }

    public DecryptedKeyPair getKeyPair(UUID teamId, String name) {
        DecryptedSecret e = getSecret(teamId, name, null);
        if (e == null) {
            return null;
        }

        Secret s = e.getSecret();
        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Invalid secret type: " + name + ", expected a key pair, got: " + e.getClass());
        }

        KeyPair k = (KeyPair) s;
        return new DecryptedKeyPair(e.getId(), k.getPublicKey());
    }

    public UUID getId(UUID teamId, String name) {
        return secretDao.getId(teamId, name);
    }

    public boolean exists(UUID teamId, String name) {
        return secretDao.getByName(teamId, name) != null;
    }

    public List<SecretEntry> list(UUID teamId) {
        return secretDao.list(teamId, SECRETS.SECRET_NAME, true);
    }

    public void delete(UUID secretId) {
        secretDao.delete(secretId);
    }

    public String generatePassword() {
        return RandomStringUtils.random(SECRET_PASSWORD_LENGTH, SECRET_PASSWORD_CHARS);
    }

    public KeyPair createKeyPair(String name, UUID teamId, String password) {
        KeyPair k = KeyPairUtils.create();
        store(name, teamId, k, password);
        return k;
    }

    public DecryptedSecret getSecret(UUID teamId, String name, String password) {
        SecretDataEntry e = secretDao.getByName(teamId, name);
        if (e == null) {
            return null;
        }

        SecretStoreType providedStoreType = getStoreType(password);
        assertStoreType(name, providedStoreType, e.getStoreType());

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        Secret s = decrypt(e.getType(), e.getData(), pwd, salt);
        return new DecryptedSecret(e.getId(), s);
    }

    public SecretDataEntry getRaw(UUID teamId, String name, String password) {
        SecretDataEntry s = secretDao.getByName(teamId, name);
        if (s == null) {
            return null;
        }

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        try {
            byte[] ab = SecretUtils.decrypt(s.getData(), pwd, salt);
            return new SecretDataEntry(s, ab);
        } catch (GeneralSecurityException e) {
            throw new SecurityException("Error decrypting a secret: " + name);
        }
    }

    public KeyPair getKeyPair(UUID teamId, String name, String password) {
        DecryptedSecret e = getSecret(teamId, name, password);
        if (e == null) {
            return null;
        }

        Secret s = e.getSecret();
        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Invalid secret type: " + name + ", expected a key pair, got: " + e.getClass());
        }

        return (KeyPair) s;
    }

    public UUID store(String name, UUID teamId, Secret s, String password) {
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

        SecretStoreType storeType = getStoreType(password);

        byte[] pwd = getPwd(password);
        byte[] salt = secretCfg.getSecretStoreSalt();

        byte[] ab;
        try {
            ab = SecretUtils.encrypt(data, pwd, salt);
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }

        return secretDao.insert(teamId, name, type, storeType, ab);
    }

    public byte[] encryptData(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.encrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    public byte[] decryptData(String projectName, byte[] data) {
        byte[] pwd = projectName.getBytes();
        try {
            return SecretUtils.decrypt(data, pwd, secretCfg.getProjectSecretsSalt());
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    private static void assertStoreType(String name, SecretStoreType provided, SecretStoreType actual) {
        if (provided == actual) {
            return;
        }

        switch (actual) {
            case SERVER_KEY:
                throw new SecurityException("Not a password-protected secret: " + name);
            case PASSWORD:
                throw new SecurityException("The secret requires a password to decrypt: " + name);
            default:
                throw new IllegalArgumentException("Unsupported secret store type: " + actual);
        }
    }

    private static Secret decrypt(SecretType type, byte[] input, byte[] password, byte[] salt) {
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

        return SecretUtils.decrypt(deserializer, input, password, salt);
    }

    private byte[] getPwd(String pwd) {
        if (pwd == null) {
            return secretCfg.getServerPwd();
        }
        return pwd.getBytes(StandardCharsets.UTF_8);
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
            throw new IllegalArgumentException("Invalid key pair data");
        }
    }

    private static SecretStoreType getStoreType(String pwd) {
        if (pwd == null) {
            return SecretStoreType.SERVER_KEY;
        }
        return SecretStoreType.PASSWORD;
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

        public DecryptedKeyPair(UUID id, byte[] data) {
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
}
