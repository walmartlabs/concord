package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.Secret;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.InjectVariable;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Named("crypto")
public class CryptoTask implements Task {

    private final RpcClient rpcClient;

    @Inject
    public CryptoTask(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

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

    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String name,
                                               String password) throws Exception {

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

    public String decryptString(@InjectVariable("txId") String instanceId, String s) throws Exception {
        return rpcClient.getSecretStoreService().decryptString(instanceId, s);
    }

    private Secret get(String instanceId, String name, String password) throws Exception {
        Secret s = rpcClient.getSecretStoreService().fetch(instanceId, name, password);
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
