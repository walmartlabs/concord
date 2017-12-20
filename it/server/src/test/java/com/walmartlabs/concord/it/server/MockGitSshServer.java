package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MockGitSshServer {

    private static final Logger log = LoggerFactory.getLogger(MockGitSshServer.class);

    private final SshServer server;

    public MockGitSshServer(int port, String repository) {
        log.info("Creating a mock git+ssh server on port {}, using {}...", port, repository);
        this.server = createServer(port, repository);
    }

    public void start() throws IOException {
        this.server.start();
    }

    public void stop() throws IOException {
        this.server.stop();
    }

    public int getPort() {
        return server.getPort();
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
