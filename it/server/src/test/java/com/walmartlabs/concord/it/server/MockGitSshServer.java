package com.walmartlabs.concord.it.server;

import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.IOException;

public class MockGitSshServer {

    private final SshServer server;

    public MockGitSshServer(int port, String repository) {
        this.server = createServer(port, repository);
    }

    public void start() throws IOException {
        this.server.start();
    }

    public void stop() throws IOException {
        this.server.stop();
    }

    private static SshServer createServer(int port, String repository) {
        SshServer s = SshServer.setUpDefaultServer();
        s.setPort(port);
        s.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        s.setPublickeyAuthenticator((username, key, session) -> true);
        s.setCommandFactory(new GitPackCommandFactory(repository));
        return s;
    }
}
