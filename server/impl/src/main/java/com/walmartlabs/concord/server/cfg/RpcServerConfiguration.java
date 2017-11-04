package com.walmartlabs.concord.server.cfg;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Serializable;

@Named
@Singleton
public class RpcServerConfiguration implements Serializable {

    private static final String RPC_SERVER_PORT_KEY = "RPC_SERVER_PORT";

    private final int port;

    public RpcServerConfiguration() {
        this.port = Integer.parseInt(Utils.getEnv(RPC_SERVER_PORT_KEY, "8101"));
    }

    public int getPort() {
        return port;
    }
}
