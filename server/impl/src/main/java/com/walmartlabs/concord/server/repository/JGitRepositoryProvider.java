package com.walmartlabs.concord.server.repository;

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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.project.RepositoryException;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import com.walmartlabs.concord.server.org.secret.SecretManager.DecryptedSecret;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SubmoduleConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.eclipse.jgit.transport.CredentialItem.Password;
import static org.eclipse.jgit.transport.CredentialItem.Username;

public class JGitRepositoryProvider implements RepositoryProvider {

    private static final Logger log = LoggerFactory.getLogger(JGitRepositoryProvider.class);

    private static final String DEFAULT_BRANCH = "master";

    private final SecretManager secretManager;

    public JGitRepositoryProvider(SecretManager secretManager) {
        this.secretManager = secretManager;
    }

    @Override
    public void fetch(UUID orgId, RepositoryEntry repository, Path dest) {
        Secret secret = getSecret(orgId, repository.getSecretName());
        if (repository.getCommitId() != null) {
            fetchByCommit(repository.getUrl(), repository.getCommitId(), secret, dest);
        } else {
            String branch = Optional.ofNullable(repository.getBranch()).orElse(DEFAULT_BRANCH);
            fetch(repository.getUrl(), branch, secret, dest);
        }
    }

    private void fetch(String uri, String branch, Secret secret, Path dest) {
        TransportConfigCallback transportCallback = createTransportConfigCallback(secret);

        try (Git repo = openRepo(dest)) {
            if (repo != null) {
                repo.checkout()
                        .setName(branch)
                        .call();

                repo.pull()
                        .setRecurseSubmodules(SubmoduleConfig.FetchRecurseSubmodulesMode.NO)
                        .setTransportConfigCallback(transportCallback)
                        .call();

                fetchSubmodules(repo.getRepository(), transportCallback);

                log.info("fetch ['{}', '{}'] -> repository updated", uri, branch);
                return;
            }
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("fetch ['{}', '{}', '{}'] -> error", uri, branch, dest, e);
            throw new RepositoryException("Error while updating a repository", e);
        }

        try (Git ignored = cloneRepo(uri, dest, branch, transportCallback)) {
            log.info("fetch ['{}', '{}'] -> initial clone completed", uri, branch);
        }
    }

    @Override
    public RepositoryManager.RepositoryInfo getInfo(Path path)  {
        try (Git repo = openRepo(path)) {
            if (repo == null) {
                return null;
            }

            Iterator<RevCommit> result = null;
            try {
                result = repo.log().setMaxCount(1).call().iterator();
            } catch (NoHeadException e) {
                return null;
            } catch (Exception e) {
                log.error("getInfo ['{}'] -> error", path, e);
                throw new RepositoryException("Error while getting a repository info", e);
            }

            if(result.hasNext()) {
                RevCommit r = result.next();
                return new RepositoryManager.RepositoryInfo(r.getId().getName(), r.getFullMessage(), r.getAuthorIdent().getName());
            }

            return null;
        }
    }

    private void fetchByCommit(String uri, String commitId, Secret secret, Path dest) {
        try (Git repo = openRepo(dest)) {
            if (repo != null) {
                log.info("fetch ['{}', '{}'] -> repository exists", uri, commitId);
                return;
            }
        }

        try (Git repo = cloneRepo(uri, dest, null, createTransportConfigCallback(secret))) {
            repo.checkout()
                    .setName(commitId)
                    .call();

            log.info("fetchByCommit ['{}', '{}'] -> initial clone completed", uri, commitId);
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            throw new RepositoryException("Error while updating a repository", e);
        }
    }

    private Secret getSecret(UUID orgId, String secretName) {
        if (secretName == null) {
            return null;
        }

        DecryptedSecret s = secretManager.getSecret(orgId, secretName, null, null);
        if (s == null) {
            throw new RepositoryException("Secret not found: " + secretName);
        }

        return s.getSecret();
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
        } catch (Exception e) {
            log.error("openRepo ['{}'] -> error", path, e);
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

            cloneSubmodules(uri, repo, transportCallback);

            return repo;
        } catch (Exception e) {
            log.error("cloneRepo ['{}', '{}', '{}'] -> error", uri, path, branch, e);
            throw new RepositoryException("Error while cloning a repository", e);
        }
    }

    private static void cloneSubmodules(String mainRepoUrl, Git repo, TransportConfigCallback transportConfigCallback) throws IOException, GitAPIException, ConfigInvalidException {
        SubmoduleInitCommand init = repo.submoduleInit();
        Collection<String> submodules = init.call();
        if (submodules.isEmpty()) {
            return;
        }

        cloneSubmodules(mainRepoUrl, repo.getRepository(), transportConfigCallback);

        // process sub-submodules
        SubmoduleWalk walk = SubmoduleWalk.forIndex(repo.getRepository());
        while (walk.next()) {
            try (Repository subRepo = walk.getRepository()) {
                if (subRepo != null) {
                    cloneSubmodules(mainRepoUrl, subRepo, transportConfigCallback);
                }
            }
        }
    }

    private static void cloneSubmodules(String mainRepoUrl, Repository repo, TransportConfigCallback transportConfigCallback) throws IOException, ConfigInvalidException, GitAPIException {
        try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
            while (walk.next()) {
                // Skip submodules not registered in .gitmodules file
                if (walk.getModulesPath() == null)
                    continue;
                // Skip submodules not registered in parent repository's config
                String url = walk.getConfigUrl();
                if (url == null)
                    continue;

                Repository submoduleRepo = walk.getRepository();
                // Clone repository if not present
                if (submoduleRepo == null) {
                    CloneCommand clone = Git.cloneRepository();
                    clone.setTransportConfigCallback(transportConfigCallback);

                    clone.setURI(url);
                    clone.setDirectory(walk.getDirectory());
                    clone.setGitDir(new File(new File(repo.getDirectory(), Constants.MODULES), walk.getPath()));
                    submoduleRepo = clone.call().getRepository();
                }

                try (RevWalk revWalk = new RevWalk(submoduleRepo)) {
                    RevCommit commit = revWalk.parseCommit(walk.getObjectId());
                    Git.wrap(submoduleRepo).checkout()
                            .setName(commit.getName())
                            .call();

                    log.info("cloneSubmodules ['{}'] -> '{}'@{}", mainRepoUrl, url, commit.getName());
                } finally {
                    if (submoduleRepo != null) {
                        submoduleRepo.close();
                    }
                }
            }
        }
    }

    private void fetchSubmodules(Repository repo, TransportConfigCallback transportCallback)
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

                Git.wrap(submoduleRepo).fetch()
                        .setRecurseSubmodules(SubmoduleConfig.FetchRecurseSubmodulesMode.NO)
                        .setTransportConfigCallback(transportCallback)
                        .call();

                fetchSubmodules(submoduleRepo, transportCallback);

                log.info("fetchSubmodules ['{}'] -> done", submoduleRepo.getDirectory());
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
            throw new RuntimeException(e);
        }
    }
}
