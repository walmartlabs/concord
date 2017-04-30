package com.walmartlabs.concord.it.server;

import com.walmartlabs.concord.it.common.ServerClient;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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

    protected StartProcessResponse start(String entryPoint, Map<String, InputStream> input) {
        return serverClient.start(entryPoint, input);
    }

    protected <T> T proxy(Class<T> klass) {
        return serverClient.proxy(klass);
    }

    protected byte[] getLog(String logFileName) {
        return serverClient.getLog(logFileName);
    }

    protected void setApiKey(String apiKey) {
        serverClient.setApiKey(apiKey);
    }

    protected void waitForLog(String logFileName, String pattern) throws IOException, InterruptedException {
        serverClient.waitForLog(logFileName, pattern);
    }
}
