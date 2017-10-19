package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.sdk.RpcConfiguration;

import javax.inject.Named;
import javax.inject.Singleton;

import static com.walmartlabs.concord.runner.ConfigurationUtils.getEnv;

@Named
@Singleton
public class RpcConfigurationImpl implements RpcConfiguration {

    private static final String SERVER_HOST_KEY = "rpc.server.host";
    private static final String SERVER_PORT_KEY = "rpc.server.port";

    private final String rpcServerHost;
    private final int rpcServerPort;

    public RpcConfigurationImpl() {
        this.rpcServerHost = getEnv(SERVER_HOST_KEY, "localhost");
        this.rpcServerPort = Integer.parseInt(getEnv(SERVER_PORT_KEY, "8101"));
    }

    @Override
    public String getServerHost() {
        return rpcServerHost;
    }

    @Override
    public int getServerPort() {
        return rpcServerPort;
    }
}
