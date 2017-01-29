package com.walmartlabs.concord.server.repository;

import com.jcraft.jsch.Session;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GitRepository {

    private static final Logger log = LoggerFactory.getLogger(GitRepository.class);

    public static Path checkout(String uri) throws IOException, GitAPIException {
        Path localPath = Files.createTempDirectory("git");
        log.info("checkout -> cloning '{}' into '{}'...", uri, localPath);

        CloneCommand cmd = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(localPath.toFile())
                .setCloneSubmodules(true);


        cmd.setTransportConfigCallback(transport -> {
            if (!(transport instanceof SshTransport)) {
                return;
            }

            SshSessionFactory f = new JschConfigSessionFactory() {
                @Override
                protected void configure(OpenSshConfig.Host hc, Session session) {
                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);
                    log.warn("checkout -> strict host key checking is disabled");
                }
            };

            SshTransport t = (SshTransport) transport;
            t.setSshSessionFactory(f);
        });

        cmd.call();

        // TODO remove .git directory?

        log.info("checkout -> cloned '{}' into '{}'", uri, localPath);
        return localPath;
    }

    private GitRepository() {
    }
}
