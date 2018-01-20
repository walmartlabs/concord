package com.walmartlabs.concord.server.repository;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.org.project.RepositoryException;
import com.walmartlabs.concord.server.org.secret.SecretManager;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;

@Named
public class GitCliRepositoryProvider implements RepositoryProvider {

    private static final Logger log = LoggerFactory.getLogger(GitCliRepositoryProvider.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    private static final String DEFAULT_BRANCH = "master";

    private final SecretManager secretManager;

    @Inject
    public GitCliRepositoryProvider(SecretManager secretManager) {
        this.secretManager = secretManager;
    }

    @Override
    public void fetch(UUID orgId, RepositoryEntry repository, Path dest) {
        Secret secret = getSecret(orgId, repository.getSecret());
        String branch = Optional.ofNullable(repository.getBranch()).orElse(DEFAULT_BRANCH);
        fetch(repository.getUrl(), branch, repository.getCommitId(), secret, dest);
    }

    @Override
    public RepositoryManager.RepositoryInfo getInfo(Path path) {
        try {
            String result = launchCommand(path, "log", "-1", "--format=%H%n%an (%ae)%n%s%n%b");
            String[] info = result.split("\n");
            if (info.length < 2) {
                return null;
            }
            String id = info[0];
            String author = info[1];
            StringBuilder message = new StringBuilder();
            for (int i = 2; i < info.length; i++) {
                message.append(info[i]).append("\n");
            }
            return new RepositoryManager.RepositoryInfo(id, author, message.toString());
        } catch (RepositoryException e) {
            // ignore
            return null;
        }
    }

    private void fetch(String uri, String branch, String commitId, Secret secret, Path dest) {
        boolean shallow = commitId == null;

        if (!hasGitRepo(dest)) {
            cloneCommand(dest, uri, secret, shallow);
        }

        launchCommand(dest, "config", "remote.origin.url", uri);

        List<RefSpec> refspecs = Collections.singletonList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        fetchCommand(dest, uri, refspecs, secret, shallow);

        ObjectId rev;
        if (commitId != null) {
            rev = getCommitRevision(dest, commitId);
        } else {
            rev = getBranchRevision(dest, branch);
        }

        checkoutCommand(dest, rev.name());
    }

    // based on the code from https://github.com/jenkinsci/git-plugin
    // MIT License, Copyright 2014 Nicolas De loof

    private ObjectId getCommitRevision(Path dest, String commitId) {
        try {
            return revParse(dest, commitId);
        } catch (RepositoryException e) {
            throw new RepositoryException("Couldn't find any revision to build. Verify the repository and commitId configuration.");
        }
    }

    private ObjectId getBranchRevision(Path dest, String branchSpec) {

        // if it doesn't contain '/' then it could be an unqualified branch
        if (!branchSpec.contains("/")) {

            // <tt>BRANCH</tt> is recognized as a shorthand of <tt>*/BRANCH</tt>
            // so check all remotes to fully qualify this branch spec
            String fqbn = "origin/" + branchSpec;
            ObjectId result = getHeadRevision(dest, fqbn);
            if (result != null) {
                return result;
            }
        } else {
            // either the branch is qualified (first part should match a valid remote)
            // or it is still unqualified, but the branch name contains a '/'
            String repository = "origin";
            String fqbn;
            if (branchSpec.startsWith(repository + "/")) {
                fqbn = "refs/remotes/" + branchSpec;
            } else if(branchSpec.startsWith("remotes/" + repository + "/")) {
                fqbn = "refs/" + branchSpec;
            } else if(branchSpec.startsWith("refs/heads/")) {
                fqbn = "refs/remotes/" + repository + "/" + branchSpec.substring("refs/heads/".length());
            } else {
                //Try branchSpec as it is - e.g. "refs/tags/mytag"
                fqbn = branchSpec;
            }

            ObjectId result = getHeadRevision(dest, fqbn);
            if (result != null) {
                return result;
            }

            //Check if exact branch name <branchSpec> exists
            fqbn = "refs/remotes/" + repository + "/" + branchSpec;
            result = getHeadRevision(dest, fqbn);
            if (result != null) {
                return result;
            }
        }

        ObjectId result = getHeadRevision(dest, branchSpec);
        if (result != null) {
            return result;
        }

        throw new RepositoryException("Couldn't find any revision to build. Verify the repository and branch configuration.");
    }

    private ObjectId getHeadRevision(Path dest, String branchSpec) {
        try {
            return revParse(dest, branchSpec);
        } catch (RepositoryException e) {
            // ignore
            return null;
        }
    }

    private boolean hasGitRepo(Path dest) {
        if (Files.notExists(dest.resolve(".git"))) {
            return false;
        }

        // Check if this is a valid git repo with --is-inside-work-tree
        try {
            launchCommand(dest, "rev-parse", "--is-inside-work-tree");
        } catch (Exception ex) {
            log.info("Workspace has a .git repository, but it appears to be corrupt.", dest);
            try {
                IOUtils.deleteRecursively(dest);
            } catch (IOException ee) {
                log.warn("hasGitRepo ['{}'] -> cleanup error: {}", dest, ee.getMessage());
            }
            return false;
        }
        return true;
    }

    private void cloneCommand(Path dest, String url, Secret secret, boolean shallow) {
        log.info("Cloning repository '{}' into '{}'", url, dest.toString());

        try {
            if (Files.notExists(dest)) {
                Files.createDirectories(dest);
            }

            // we don't run a 'git clone' command but git init + git fetch
            // this allows launchCommandWithCredentials() to pass credentials via a local gitconfig

            // init
            launchCommand(dest, "init");

            // fetch
            List<RefSpec> refspecs = Collections.singletonList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
            fetchCommand(dest, url, refspecs, secret, shallow);

            launchCommand(dest, "config", "remote.origin.url", url);

            for (RefSpec refSpec : refspecs) {
                launchCommand(dest, "config", "--add", "remote.origin.fetch", refSpec.toString());
            }
        } catch (IOException e) {
            log.error("cloneCommand ['{}'] -> error", dest, e);
            throw new RepositoryException("clone repository error: " + e.getMessage());
        }
    }

    private void fetchCommand(Path dest, String url, List<RefSpec> refspecs, Secret secret, boolean shallow) {
        log.info("Fetching upstream changes from '{}'", url);

        List<String> args = new ArrayList<>();
        args.add("fetch");
        if (shallow) {
            args.add("--depth=1");
        }

        args.add("--tags");

        args.add(url);

        for (RefSpec r : refspecs) {
            args.add(r.toString());
        }

        launchCommandWithCredentials(dest, args, secret);
    }

    private void checkoutCommand(Path dest, String ref) {
        log.info("Checking out revision '{}'", ref);

        launchCommand(dest, "checkout", "-f", ref);
    }

    private ObjectId revParse(Path dest, String revName) {
        String arg = revName + "^{commit}";
        String result = launchCommand(dest, "rev-parse", arg);
        String line = result.trim();
        if (line.isEmpty())
            throw new RepositoryException("rev-parse no content returned for " + revName);
        return ObjectId.fromString(line);
    }

    private void launchCommandWithCredentials(Path workDir, List<String> args, Secret secret) {

        Path key = null;
        Path ssh = null;
        Path askpass = null;

        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0"); // Don't prompt for auth from command line git

        try {
            if (secret instanceof KeyPair) {
                KeyPair keyPair = (KeyPair) secret;

                key = createSshKeyFile(keyPair);
                ssh =  createUnixGitSSH(key);

                env.put("GIT_SSH", ssh.toAbsolutePath().toString());

                // supply a dummy value for DISPLAY if not already present
                // or else ssh will not invoke SSH_ASKPASS
                if (!env.containsKey("DISPLAY")) {
                    env.put("DISPLAY", ":");
                }

                log.info("using GIT_SSH to set credentials");
            } else if (secret instanceof UsernamePassword) {
                UsernamePassword userPass = (UsernamePassword) secret;

                askpass = createUnixStandardAskpass(userPass);

                env.put("GIT_ASKPASS", askpass.toAbsolutePath().toString());
                // SSH binary does not recognize GIT_ASKPASS, so set SSH_ASKPASS also, in the case we have an ssh:// URL
                env.put("SSH_ASKPASS", askpass.toAbsolutePath().toString());

                log.info("using GIT_ASKPASS to set credentials ");
            }

            launchCommand(workDir, env, args);
        } catch (IOException e) {
            throw new RepositoryException("Failed to setup credentials", e);
        } finally {
            deleteTempFile(key);
            deleteTempFile(ssh);
            deleteTempFile(askpass);
        }
    }

    private String launchCommand(Path workDir, String... args) throws RepositoryException {
        List<String> listArgs = Arrays.asList(args);
        Map<String, String> env = new HashMap<>();
        return launchCommand(workDir,env, listArgs);
    }

    private String launchCommand(Path workDir, Map<String, String> envVars, List<String> args) {

        List<String> cmd = ImmutableList.<String>builder().add("git").addAll(args).build();

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir.toFile());

        Map<String, String> env = pb.environment();
        env.putAll(envVars);

        // If we don't have credentials, but the requested URL requires them,
        // it is possible for Git to hang forever waiting for interactive
        // credential input. Prevent this by setting GIT_ASKPASS to "echo"
        // if we haven't already set it.
        if (!env.containsKey("GIT_ASKPASS")) {
            env.put("GIT_ASKPASS", "echo");
        }

        log.info("> {}", String.join(" ", cmd));

        try {
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("GIT: {}", line);
                out.append(line).append("\n");
            }

            StringBuilder error = new StringBuilder();
            reader = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = reader.readLine()) != null) {
                error.append(line).append("\n");
            }

            int code = p.waitFor();
            if (code != SUCCESS_EXIT_CODE) {
                log.warn("launchCommand ['{}'] -> finished with code {}, error: '{}'", cmd, code, error.toString());
                throw new RepositoryException(error.toString());
            }

            return out.toString();
        } catch (IOException | InterruptedException e) {
            log.error("launchCommand ['{}'] -> error", cmd, e);
            throw new RepositoryException("git operation error: " + e.getMessage());
        }
    }

    private Path createUnixStandardAskpass(UsernamePassword creds) throws IOException {
        Path askpass = IOUtils.createTempFile("pass", ".sh");
        try (PrintWriter w = new PrintWriter(askpass.toFile(), Charset.defaultCharset().toString())) {
            w.println("#!/bin/sh");
            w.println("case \"$1\" in");
            w.println("Username*) echo '" + quoteUnixCredentials(creds.getUsername()) + "' ;;");
            w.println("Password*) echo '" + quoteUnixCredentials(new String(creds.getPassword())) + "' ;;");
            w.println("esac");
        }
        Files.setPosixFilePermissions(askpass, ImmutableSet.of(OWNER_READ, OWNER_EXECUTE));
        return askpass;
    }

    private Path createSshKeyFile(KeyPair keyPair) throws IOException {
        Path keyFile = IOUtils.createTempFile("ssh", ".key");

        // TODO: chmod ?
        Files.write(keyFile, keyPair.getPrivateKey());

        return keyFile;
    }

    private Path createUnixGitSSH(Path key) throws IOException {
        Path ssh = IOUtils.createTempFile("ssh", ".sh");

        try (PrintWriter w = new PrintWriter(ssh.toFile(), Charset.defaultCharset().toString())) {
            w.println("#!/bin/sh");
            // ${SSH_ASKPASS} might be ignored if ${DISPLAY} is not set
            w.println("if [ -z \"${DISPLAY}\" ]; then");
            w.println("  DISPLAY=:123.456");
            w.println("  export DISPLAY");
            w.println("fi");
            w.println("ssh -i \"" + key.toAbsolutePath().toString() + "\" -o StrictHostKeyChecking=no \"$@\"");
        }
        Files.setPosixFilePermissions(ssh, ImmutableSet.of(OWNER_READ, OWNER_EXECUTE));
        return ssh;
    }

    private String quoteUnixCredentials(String str) {
        // Assumes string will be used inside of single quotes, as it will
        // only replace "'" substrings.
        return str.replace("'", "'\\''");
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.delete(tempFile);
        } catch (IOException e) {
            log.warn("can't delete tmp file: " + tempFile);
        }
    }

    private Secret getSecret(UUID orgId, String secretName) {
        if (secretName == null) {
            return null;
        }

        SecretManager.DecryptedSecret s = secretManager.getSecret(orgId, secretName, null, null);
        if (s == null) {
            throw new RepositoryException("Secret not found: " + secretName);
        }

        return s.getSecret();
    }
}
