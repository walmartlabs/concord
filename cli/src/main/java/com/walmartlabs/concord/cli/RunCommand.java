package com.walmartlabs.concord.cli;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(commandDescription = "Create and run a payload")
public class RunCommand {

    public static final String COMMAND_NAME = "run";

    @Parameter(names = "-f", arity = 1,
            description = "Payload JSON descriptor")
    private String descriptor = "build.json";

    @Parameter(names = "-s", arity = 1,
            description = "Server address, host:port",
            validateWith = Main.ServerAddressValidator.class)
    private String serverAddr = "localhost:8001";

    public String getDescriptor() {
        return descriptor;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    @Override
    public String toString() {
        return "RunCommand{" +
                "descriptor='" + descriptor + '\'' +
                ", serverAddr='" + serverAddr + '\'' +
                '}';
    }
}
