package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.it.common.ServerClient;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.security.secret.UploadSecretResponse;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractServerIT {

    private ServerClient serverClient;

    @Before
    public void _init() throws Exception {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    @After
    public void _destroy() {
        serverClient.close();
    }

    protected StartProcessResponse start(Map<String, Object> input) {
        return start(input, false);
    }

    protected StartProcessResponse start(Map<String, Object> input, boolean sync) {
        return serverClient.start(input, sync);
    }

    protected StartProcessResponse start(String entryPoint, Map<String, Object> input) {
        return serverClient.start(entryPoint, input, false);
    }

    protected UploadSecretResponse addPlainSecret(String name, boolean generatePassword, String storePassword, byte[] secret) {
        return serverClient.addPlainSecret(name, generatePassword, storePassword, secret);
    }

    protected UploadSecretResponse addUsernamePassword(String name, boolean generatePassword, String storePassword, String username, String password) {
        return serverClient.addUsernamePassword(name, generatePassword, storePassword, username, password);
    }

    protected <T> T proxy(Class<T> klass) {
        return serverClient.proxy(klass);
    }

    protected byte[] getLog(String logFileName) {
        return serverClient.getLog(logFileName);
    }

    protected void resetApiKey() {
        serverClient.resetApiKey();
    }

    protected void setApiKey(String apiKey) {
        serverClient.setApiKey(apiKey);
    }

    protected void setGithubKey(String key) {
        serverClient.setGithubKey(key);
    }

    protected void waitForLog(String logFileName, String pattern) throws IOException, InterruptedException {
        serverClient.waitForLog(logFileName, pattern);
    }

    private static final char[] RANDOM_CHARS = "abcdef0123456789".toCharArray();

    protected String randomString() {
        StringBuilder b = new StringBuilder();
        b.append(System.currentTimeMillis()).append("_");

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            int n = rng.nextInt(RANDOM_CHARS.length);
            b.append(RANDOM_CHARS[n]);
        }

        return b.toString();
    }
}
