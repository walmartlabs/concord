package com.walmartlabs.concord.plugins.crypto;

import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.SecretStoreService;
import com.walmartlabs.concord.sdk.RpcClient;
import com.walmartlabs.concord.sdk.SecretStore;
import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Named("crypto")
public class CryptoTask implements Task, SecretStore {

    private static final Logger log = LoggerFactory.getLogger(CryptoTask.class);

    private final SecretStoreService secretStoreService;

    @Inject
    public CryptoTask(RpcClient rpcClient) {
        this.secretStoreService = rpcClient.getSecretStoreService();
    }

    @Override
    public String exportAsString(@InjectVariable("txId") String instanceId,
                                 String name,
                                 String password) throws Exception {

        Secret s = get(instanceId, name, password);

        if (!(s instanceof BinaryDataSecret)) {
            throw new IllegalArgumentException("The secret '" + name + "'can't be exported as a string");
        }

        BinaryDataSecret bds = (BinaryDataSecret) s;
        return new String(bds.getData());
    }

    @Override
    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String name,
                                               String password) throws Exception {

        log.info("Exporting a key pair: {}", name);

        Secret s = get(instanceId, name, password);
        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Expected a key pair: ");
        }

        KeyPair kp = (KeyPair) s;

        Path baseDir = Paths.get(workDir);
        Path tmpDir = assertTempDir(baseDir);

        Path privateKey = Files.createTempFile(tmpDir, "private", ".key");
        Files.write(privateKey, kp.getPrivateKey());

        Path publicKey = Files.createTempFile(tmpDir, "public", ".key");
        Files.write(publicKey, kp.getPublicKey());

        Map<String, String> m = new HashMap<>();
        m.put("private", baseDir.relativize(privateKey).toString());
        m.put("public", baseDir.relativize(publicKey).toString());

        return m;
    }

    @Override
    public Map<String, String> exportCredentials(@InjectVariable("txId") String instanceId,
                                                 @InjectVariable("workDir") String workDir,
                                                 String name,
                                                 String password) throws Exception {
        Secret s = get(instanceId, name, password);
        if (!(s instanceof UsernamePassword)) {
            throw new IllegalArgumentException("Expected a key pair: ");
        }

        UsernamePassword up = (UsernamePassword) s;

        Map<String, String> m = new HashMap<>();
        m.put("username", up.getUsername());
        m.put("password", new String(up.getPassword()));
        return m;
    }

    @Override
    public String decryptString(@InjectVariable("txId") String instanceId, String s) throws Exception {
        return secretStoreService.decryptString(instanceId, s);
    }

    private Secret get(String instanceId, String name, String password) throws Exception {
        Secret s = secretStoreService.fetch(instanceId, name, password);
        if (s == null) {
            throw new IllegalArgumentException("Secret not found: " + name);
        }
        return s;
    }

    private static Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(".tmp");
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }
}
