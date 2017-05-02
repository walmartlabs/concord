package com.walmartlabs.concord.server.project;

import com.google.common.base.Throwables;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import com.walmartlabs.concord.server.security.secret.KeyPair;
import com.walmartlabs.concord.server.security.secret.Secret;
import com.walmartlabs.concord.server.security.secret.UsernamePassword;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import static org.eclipse.jgit.transport.CredentialItem.Password;
import static org.eclipse.jgit.transport.CredentialItem.Username;

@Named
public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);
    private static final String DEFAULT_BRANCH = "master";

    private final RepositoryConfiguration cfg;

    @Inject
    public RepositoryManager(RepositoryConfiguration cfg) {
        this.cfg = cfg;
    }

    public Path fetch(String projectName, String uri, String branch, Secret secret) throws RepositoryException {
        if (branch == null) {
            branch = DEFAULT_BRANCH;
        }

        TransportConfigCallback transportCallback = transport -> {
            if (transport instanceof SshTransport) {
                configureSshTransport((SshTransport) transport, secret);
            } else if (transport instanceof HttpTransport) {
                configureHttpTransport((HttpTransport) transport, secret);
            }
        };

        Git repo = null;

        Path localPath = cfg.getRepoCacheDir().resolve(projectName).resolve(branch);
        if (Files.exists(localPath)) {
            // check if there is an existing git repo
            try {
                repo = Git.open(localPath.toFile());
            } catch (RepositoryNotFoundException e) {
            } catch (IOException e) {
                throw new RepositoryException("Error while opening a repository", e);
            }

        } else {
            try {
                Files.createDirectories(localPath);
            } catch (IOException e) {
                throw new RepositoryException("Can't create a directory for a repository", e);
            }
        }

        if (repo == null) {
            try {
                Git.cloneRepository()
                        .setURI(uri)
                        .setDirectory(localPath.toFile())
                        .setBranch(branch)
                        .setBranchesToClone(Collections.singleton(branch))
                        .setTransportConfigCallback(transportCallback)
                        .call();

                // we are done
                log.info("fetch ['{}', '{}', '{}'] -> initial clone completed", projectName, uri, branch);
                return localPath;
            } catch (GitAPIException e) {
                try {
                    IOUtils.deleteRecursively(localPath);
                } catch (IOException ee) {
                    log.warn("fetch ['{}', '{}', '{}'] -> cleanup error: {}", projectName, uri, branch, ee.getMessage());
                }
                throw new RepositoryException("Error while cloning a repository", e);
            }
        }

        try {
            repo.checkout()
                    .setName(branch)
                    .call();

            repo.pull()
                    .setTransportConfigCallback(transportCallback)
                    .call();
            log.info("fetch ['{}', '{}', '{}'] -> repository updated", projectName, uri, branch);
        } catch (GitAPIException e) {
            throw new RepositoryException("Error while updating a repository", e);
        }

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
}
