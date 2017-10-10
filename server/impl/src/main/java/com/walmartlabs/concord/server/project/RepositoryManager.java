package com.walmartlabs.concord.server.project;

import com.google.common.base.Throwables;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.Secret;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.server.cfg.RepositoryConfiguration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SubmoduleConfig;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
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
import java.util.UUID;

import static org.eclipse.jgit.transport.CredentialItem.Password;
import static org.eclipse.jgit.transport.CredentialItem.Username;

@Named
public class RepositoryManager {

    private static final Logger log = LoggerFactory.getLogger(RepositoryManager.class);
    public static final String DEFAULT_BRANCH = "master";

    private final RepositoryConfiguration cfg;

    @Inject
    public RepositoryManager(RepositoryConfiguration cfg) {
        this.cfg = cfg;
    }

    public void testConnection(String uri, String branch, String commitId, String path, Secret secret) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory("repository");

            if (commitId != null) {
                branch = null;
            }

            try (Git repo = cloneRepo(uri, tmpDir, branch, createTransportConfigCallback(secret))) {
                if (commitId != null) {
                    repo.checkout()
                            .setName(commitId)
                            .call();
                }
            }

            repoPath(tmpDir, path);
        } catch (GitAPIException | IOException e) {
            throw new RepositoryException(e);
        } finally {
            if (tmpDir != null) {
                try {
                    IOUtils.deleteRecursively(tmpDir);
                } catch (IOException e) {
                    log.warn("testConnection -> cleanup error: {}", e.getMessage());
                }
            }
        }
    }

    public Path fetchByCommit(UUID projectId, String repoName, String uri, String commitId, String path, Secret secret) {
        Path localPath = localPath(projectId, repoName, commitId);

        try (Git repo = openRepo(localPath)) {
            if (repo != null) {
                log.info("fetch ['{}', '{}', '{}'] -> repository exists", projectId, uri, commitId);
                return localPath;
            }
        }

        try (Git repo = cloneRepo(uri, localPath, null, createTransportConfigCallback(secret))) {
            repo.checkout()
                    .setName(commitId)
                    .call();

            log.info("fetchByCommit ['{}', '{}', '{}'] -> initial clone completed", projectId, uri, commitId);
        } catch (GitAPIException e) {
            throw new RepositoryException("Error while updating a repository", e);
        }

        return repoPath(localPath, path);
    }

    public Path getRepoPath(UUID projectId, String repoName, String branch, String path) {
        if (branch == null) {
            branch = DEFAULT_BRANCH;
        }

        Path localPath = localPath(projectId, repoName, branch);
        return repoPath(localPath, path);
    }

    public Path fetch(UUID projectId, String repoName, String uri, String branch, String path, Secret secret) {
        if (branch == null) {
            branch = DEFAULT_BRANCH;
        }

        TransportConfigCallback transportCallback = createTransportConfigCallback(secret);

        Path localPath = localPath(projectId, repoName, branch);
        try (Git repo = openRepo(localPath)) {
            if (repo != null) {
                repo.checkout()
                        .setName(branch)
                        .call();

                repo.pull()
                        .setRecurseSubmodules(SubmoduleConfig.FetchRecurseSubmodulesMode.NO)
                        .setTransportConfigCallback(transportCallback)
                        .call();

                fetchSubmodules(projectId, repo.getRepository(), transportCallback);

                log.info("fetch ['{}', '{}', '{}'] -> repository updated", projectId, uri, branch);

                return repoPath(localPath, path);
            }
        } catch (GitAPIException e) {
            throw new RepositoryException("Error while updating a repository", e);
        }

        try (Git ignored = cloneRepo(uri, localPath, branch, transportCallback)) {
            log.info("fetch ['{}', '{}', '{}'] -> initial clone completed", projectId, uri, branch);
            return repoPath(localPath, path);
        }
    }

    private Path localPath(UUID projectId, String repoName, String branch) {
        return cfg.getRepoCacheDir()
                .resolve(String.valueOf(projectId))
                .resolve(repoName)
                .resolve(branch);
    }

    private static Git openRepo(Path path) {
        if (!Files.exists(path)) {
            return null;
        }

        // check if there is an existing git repo
        try {
            return Git.open(path.toFile());
        } catch (RepositoryNotFoundException e) {
            // ignore
        } catch (IOException e) {
            throw new RepositoryException("Error while opening a repository", e);
        }

        return null;
    }

    private static Git cloneRepo(String uri, Path path, String branch, TransportConfigCallback transportCallback) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RepositoryException("Can't create a directory for a repository", e);
            }
        }

        try {
            Git repo = Git.cloneRepository()
                    .setURI(uri)
                    .setCloneSubmodules(true)
                    .setBranch(branch)
                    .setBranchesToClone(branch != null ? Collections.singleton(branch) : null)
                    .setDirectory(path.toFile())
                    .setTransportConfigCallback(transportCallback)
                    .call();

            // check if the branch actually exists
            if (branch != null) {
                repo.checkout()
                        .setName(branch)
                        .call();
            }

            return repo;
        } catch (GitAPIException e) {
            try {
                IOUtils.deleteRecursively(path);
            } catch (IOException ee) {
                log.warn("cloneRepo ['{}', '{}'] -> cleanup error: {}", uri, branch, ee.getMessage());
            }
            throw new RepositoryException("Error while cloning a repository", e);
        }
    }

    private void fetchSubmodules(UUID projectId, Repository repo, TransportConfigCallback transportCallback)
            throws GitAPIException {
        try (SubmoduleWalk walk = new SubmoduleWalk(repo);
             RevWalk revWalk = new RevWalk(repo)) {
            // Walk over submodules in the parent repository's FETCH_HEAD.
            ObjectId fetchHead = repo.resolve(Constants.FETCH_HEAD);
            if (fetchHead == null) {
                return;
            }
            walk.setTree(revWalk.parseTree(fetchHead));
            while (walk.next()) {
                Repository submoduleRepo = walk.getRepository();

                // Skip submodules that don't exist locally (have not been
                // cloned), are not registered in the .gitmodules file, or
                // not registered in the parent repository's config.
                if (submoduleRepo == null || walk.getModulesPath() == null
                        || walk.getConfigUrl() == null) {
                    continue;
                }

                new Git(submoduleRepo).fetch()
                        .setRecurseSubmodules(SubmoduleConfig.FetchRecurseSubmodulesMode.NO)
                        .setTransportConfigCallback(transportCallback)
                        .call();

                fetchSubmodules(projectId, submoduleRepo, transportCallback);

                log.info("fetchSubmodules ['{}', '{}'] -> done", projectId, submoduleRepo.getDirectory());
            }
        } catch (IOException e) {
            throw new JGitInternalException(e.getMessage(), e);
        } catch (ConfigInvalidException e) {
            throw new InvalidConfigurationException(e.getMessage(), e);
        }
    }

    private static TransportConfigCallback createTransportConfigCallback(Secret secret) {
        return transport -> {
            if (transport instanceof SshTransport) {
                configureSshTransport((SshTransport) transport, secret);
            } else if (transport instanceof HttpTransport) {
                configureHttpTransport((HttpTransport) transport, secret);
            }
        };
    }

    private static void configureSshTransport(SshTransport t, Secret secret) {
        if (!(secret instanceof KeyPair)) {
            throw new RepositoryException("Invalid secret type, expected a key pair");
        }

        SshSessionFactory f = new JschConfigSessionFactory() {

            @Override
            protected JSch createDefaultJSch(FS fs) throws JSchException {
                JSch d = super.createDefaultJSch(fs);
                if (secret == null) {
                    return d;
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
                throw new RepositoryException("Invalid secret type, expected a username/password credentials");
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

    private static String normalizePath(String s) {
        if (s == null) {
            return null;
        }

        while (s.startsWith("/")) {
            s = s.substring(1);
        }

        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }

        if (s.trim().isEmpty()) {
            return null;
        }

        return s;
    }

    private static Path repoPath(Path baseDir, String p) {
        String normalized = normalizePath(p);
        if (normalized == null) {
            return baseDir;
        }

        Path repoDir = baseDir.resolve(normalized);
        if (!Files.exists(repoDir)) {
            throw new RepositoryException("Invalid repository path: " + p);
        }

        return repoDir;
    }
}
