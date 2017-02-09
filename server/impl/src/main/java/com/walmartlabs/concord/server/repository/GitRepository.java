package com.walmartlabs.concord.server.repository;

import com.google.common.base.Throwables;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.walmartlabs.concord.server.security.secret.KeyPair;
import com.walmartlabs.concord.server.security.secret.Secret;
import com.walmartlabs.concord.server.security.secret.UsernamePassword;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.CredentialItem.Password;
import org.eclipse.jgit.transport.CredentialItem.Username;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GitRepository {

    private static final Logger log = LoggerFactory.getLogger(GitRepository.class);
    private static final String DEFAULT_BRANCH = "master";

    public static Path checkout(String uri, String branch, Secret secret) throws IOException, GitAPIException {
        if (branch == null || branch.trim().isEmpty()) {
            branch = DEFAULT_BRANCH;
        }

        Path localPath = Files.createTempDirectory("git");
        log.info("checkout -> cloning '{}' (branch: {}) into '{}'...", uri, branch, localPath);

        TransportConfigCallback transportCallback = transport -> {
            if (transport instanceof SshTransport) {
                configureSshTransport((SshTransport) transport, secret);
            } else if (transport instanceof HttpTransport) {
                configureHttpTransport((HttpTransport) transport, secret);
            }
        };

        Git git = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(localPath.toFile())
                .setBranch(branch)
                .setTransportConfigCallback(transportCallback)
                .call();

        String currentBranch = git.getRepository().getBranch();
        if (!branch.equals(currentBranch)) {
            log.error("checkout ['{}'] -> can't checkout the branch. Expected: '{}', got: '{}'", uri, branch, currentBranch);
            throw new IllegalArgumentException("Can't checkout the branch: " + branch);
        }

        log.info("checkout -> cloned '{}' into '{}'", uri, localPath);
        return localPath;
    }

    private static void configureSshTransport(SshTransport t, Secret secret) {
        SshSessionFactory f = new JschConfigSessionFactory() {

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch d = super.createDefaultJSch(fs);
                if (secret == null) {
                    return d;
                }

                if (!(secret instanceof KeyPair)) {
                    throw new IllegalArgumentException("Invalid secret type, expected a key pair");
                }

                d.removeAllIdentity();

                KeyPair kp = (KeyPair) secret;
                d.addIdentity("concord-server", kp.getPrivateKey(), kp.getPublicKey(), null);
                log.debug("configureSshTransport -> using the supplied secret");
                return d;
            }

            @Override
            protected void configure(OpenSshConfig.Host hc, Session session) {
                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                log.warn("configureSshTransport -> strict host key checking is disabled");
            }
        };

        t.setSshSessionFactory(f);
    }

    private static void configureHttpTransport(HttpTransport t, Secret secret) {
        if (secret != null) {
            if (!(secret instanceof UsernamePassword)) {
                throw new IllegalArgumentException("Invalid secret type, expected a username/password credentials");
            }

            UsernamePassword up = (UsernamePassword) secret;

            t.setCredentialsProvider(new CredentialsProvider() {
                @Override
                public boolean isInteractive() {
                    return false;
                }

                @Override
                public boolean supports(CredentialItem... items) {
                    return true;
                }

                @Override
                public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
                    int cnt = 0;

                    for (CredentialItem i : items) {
                        if (i instanceof Username) {
                            ((Username) i).setValue(up.getUsername());
                            cnt += 1;
                        } else if (i instanceof Password) {
                            ((Password) i).setValue(up.getPassword());
                            cnt += 1;
                        }
                    }

                    boolean ok = cnt == 2;
                    if (ok) {
                        log.debug("configureHttpTransport -> using the supplied secret");
                    }

                    return ok;
                }
            });
        }

        try {
            // unfortunately JGit doesn't expose sslVerify in the clone command
            // use reflection to disable it

            Field cfgField = t.getClass().getDeclaredField("http");
            cfgField.setAccessible(true);

            Object cfg = cfgField.get(t);
            if (cfg == null) {
                log.warn("configureHttpTransport -> can't disable SSL verification");
                return;
            }

            Field paramField = cfg.getClass().getDeclaredField("sslVerify");
            paramField.setAccessible(true);
            paramField.set(cfg, false);

            log.warn("configureHttpTransport -> SSL verification is disabled");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }

    private GitRepository() {
    }
}
