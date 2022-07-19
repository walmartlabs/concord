package com.walmartlabs.concord.it.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import org.apache.sshd.git.GitLocationResolver;
import org.apache.sshd.git.pack.GitPackCommand;
import org.apache.sshd.git.pack.GitPackCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class MockGitSshServer {

    private static final Logger log = LoggerFactory.getLogger(MockGitSshServer.class);

    private final SshServer server;

    public MockGitSshServer(int port, Path repository) {
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

    private static SshServer createServer(int port, Path repository) {
        SshServer s = SshServer.setUpDefaultServer();
        s.setPort(port);
        s.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        s.setPublickeyAuthenticator((username, key, session) -> true);
        s.setCommandFactory(new GitPackCommandFactory(GitLocationResolver.constantPath(repository)) {
            @Override
            public GitPackCommand createGitCommand(String command) {
                return new GitPackCommand(getGitLocationResolver(), command, resolveExecutorService(command)) {
                    @Override
                    protected Path resolveRootDirectory(String command, String[] args) {
                        return repository;
                    }
                };
            }
        });
        return s;
    }
}
