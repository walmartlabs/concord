package com.walmartlabs.concord.repository;

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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;

/**
 * A GIT CLI wrapper. Most of the code was lifted from Jenkins' git-plugin.
 */
public class GitClient {

    private static final Logger log = LoggerFactory.getLogger(GitClient.class);

    private static final int SUCCESS_EXIT_CODE = 0;

    private final long defaultTimeout;
    private final GitClientConfiguration cfg;

    private final List<String> sensitiveData;
    private final ExecutorService executor;

    public GitClient(GitClientConfiguration cfg) {
        this.cfg = cfg;
        this.sensitiveData = cfg.oauthToken() != null ? Collections.singletonList(cfg.oauthToken()) : Collections.emptyList();
        this.executor = Executors.newCachedThreadPool();
        this.defaultTimeout = cfg.defaultOperationTimeout().toMillis();
    }

    public RepositoryInfo getInfo(Path path) {
        String result = launchCommand(path, defaultTimeout, "log", "-1", "--format=%H%n%an (%ae)%n%s%n%b");
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
        return new RepositoryInfo(id, message.toString(), author);
    }

    public String fetch(String uri, String branch, String commitId, Secret secret, Path dest) {
        return fetch(uri, branch, commitId, true, secret, dest);
    }

    public String fetch(String uri, String branch, String commitId, boolean detached, Secret secret, Path dest) {
        // can use shallow clone only with branch/tag
        boolean shallow = commitId == null && cfg.shallowClone();

        if (!hasGitRepo(dest)) {
            cloneCommand(uri, secret, shallow, dest);
        }

        launchCommand(dest, defaultTimeout, "config", "remote.origin.url", uri);

        List<RefSpec> refspecs = Collections.singletonList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        fetchCommand(uri, refspecs, secret, shallow, dest);

        String rev;
        if (commitId != null) {
            rev = getCommitRevision(commitId, dest).name();
        } else if (detached) {
            rev = getBranchRevision(branch, dest).name();
        } else {
            rev = branch;
        }

        checkoutCommand(rev, dest);

        launchCommand(dest, defaultTimeout, "clean", "-fdx");

        if (hasGitModules(dest)) {
            fetchSubmodules(secret, dest);

            launchCommand(dest, defaultTimeout, "submodule", "foreach", "git", "reset", "--hard");
        }
        return rev;
    }

    private void fetchCommand(String url,
                              List<RefSpec> refspecs,
                              Secret secret,
                              boolean shallow,
                              Path dest) {
        log.info("Fetching upstream changes from '{}'", hideSensitiveData(url));

        List<String> args = new ArrayList<>();
        args.add("fetch");
        if (shallow) {
            args.add("--depth=1");
        }

        args.add("--tags");

        args.add(processUrl(url, secret));

        for (RefSpec r : refspecs) {
            args.add(r.toString());
        }

        launchCommandWithCredentials(dest, cfg.fetchTimeout().toMillis(), args, secret);
    }

    private void fetchSubmodules(Secret secret, Path dest) {
        launchCommand(dest, defaultTimeout, "submodule", "init");
        launchCommand(dest, defaultTimeout, "submodule", "sync");

        List<String> args = new ArrayList<>();
        args.add("submodule");
        args.add("update");

        args.add("--init");
        args.add("--recursive");

        String[] modulePaths;

        try {
            modulePaths = launchCommand(dest, defaultTimeout, "config", "--file", ".gitmodules", "--name-only", "--get-regexp", "path")
                    .split("\\r?\\n");
        } catch (RepositoryException e) {
            log.warn("fetchSubmodules ['{}'] -> error while retrieving the list of submodules: {}", dest, e.getMessage());
            return;
        }

        for (String mp : modulePaths) {
            if (mp.trim().isEmpty()) {
                continue;
            }

            String moduleName = mp.substring("submodule.".length(), mp.length() - ".path".length());

            // Find the URL for this submodule
            String url = getSubmoduleUrl(dest, moduleName);
            if (url == null) {
                throw new RepositoryException("Empty repository for " + moduleName);
            }

            String pUrl = processUrl(url, secret);
            if (!pUrl.equals(url)) {
                launchCommand(dest, defaultTimeout, "config", "submodule." + moduleName + ".url", pUrl);
            }

            // Find the path for this submodule
            String sModulePath = getSubmodulePath(dest, moduleName);

            List<String> perModuleArgs = new ArrayList<>(args);
            perModuleArgs.add(sModulePath);
            launchCommandWithCredentials(dest, cfg.fetchTimeout().toMillis(), perModuleArgs, secret);
        }
    }

    private String getSubmoduleUrl(Path dest, String name) {
        String result = launchCommand(dest, defaultTimeout, "config", "--get", "submodule." + name + ".url");
        String s = firstLine(result);
        return s != null ? s.trim() : null;
    }

    private String getSubmodulePath(Path dest, String name) {
        String result = launchCommand(dest, defaultTimeout, "config", "-f", ".gitmodules", "--get", "submodule." + name + ".path");
        String s = firstLine(result);
        return s != null ? s.trim() : null;
    }

    private ObjectId getCommitRevision(String commitId, Path dest) {
        try {
            return revParse(commitId, dest);
        } catch (RepositoryException e) {
            throw new RepositoryException("Couldn't find any revision to build. Verify the repository and commitId configuration.");
        }
    }

    private ObjectId revParse(String revName, Path dest) {
        String arg = revName + "^{commit}";
        String result = launchCommand(dest, defaultTimeout, "rev-parse", arg);
        String line = result.trim();
        if (line.isEmpty()) {
            throw new RepositoryException("rev-parse no content returned for " + revName);
        }
        return ObjectId.fromString(line);
    }

    private ObjectId getBranchRevision(String branchSpec, Path dest) {

        // if it doesn't contain '/' then it could be an unqualified branch
        if (!branchSpec.contains("/")) {

            // <tt>BRANCH</tt> is recognized as a shorthand of <tt>*/BRANCH</tt>
            // so check all remotes to fully qualify this branch spec
            String fqbn = "origin/" + branchSpec;
            ObjectId result = getHeadRevision(fqbn, dest);
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
            } else if (branchSpec.startsWith("remotes/" + repository + "/")) {
                fqbn = "refs/" + branchSpec;
            } else if (branchSpec.startsWith("refs/heads/")) {
                fqbn = "refs/remotes/" + repository + "/" + branchSpec.substring("refs/heads/".length());
            } else {
                //Try branchSpec as it is - e.g. "refs/tags/mytag"
                fqbn = branchSpec;
            }

            ObjectId result = getHeadRevision(fqbn, dest);
            if (result != null) {
                return result;
            }

            //Check if exact branch name <branchSpec> exists
            fqbn = "refs/remotes/" + repository + "/" + branchSpec;
            result = getHeadRevision(fqbn, dest);
            if (result != null) {
                return result;
            }
        }

        ObjectId result = getHeadRevision(branchSpec, dest);
        if (result != null) {
            return result;
        }

        throw new RepositoryException("Couldn't find any revision to build. Verify the repository and branch configuration.");
    }

    private ObjectId getHeadRevision(String branchSpec, Path dest) {
        try {
            return revParse(branchSpec, dest);
        } catch (RepositoryException e) {
            // ignore
            return null;
        }
    }

    private String firstLine(String result) {
        BufferedReader reader = new BufferedReader(new StringReader(result));
        String line;
        try {
            line = reader.readLine();
            if (line == null) {
                return null;
            }
            if (reader.readLine() != null) { // NOSONAR
                throw new RepositoryException("Unexpected multiple lines: " + result);
            }
        } catch (IOException e) {
            throw new RepositoryException("Error parsing result", e);
        }

        return line;
    }

    private boolean hasGitRepo(Path dest) {
        return Files.exists(dest.resolve(".git"));
    }

    private boolean hasGitModules(Path dest) {
        return Files.exists(dest.resolve(".gitmodules"));
    }

    private void cloneCommand(String url, Secret secret, boolean shallow, Path dest) {
        log.info("Cloning repository '{}' into '{}'", hideSensitiveData(url), dest.toString());

        try {
            if (Files.notExists(dest)) {
                Files.createDirectories(dest);
            }

            // init
            launchCommand(dest, defaultTimeout, "init");

            // fetch
            List<RefSpec> refspecs = Collections.singletonList(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
            fetchCommand(url, refspecs, secret, shallow, dest);

            launchCommand(dest, defaultTimeout, "config", "remote.origin.url", url);

            for (RefSpec refSpec : refspecs) {
                launchCommand(dest, defaultTimeout, "config", "--add", "remote.origin.fetch", refSpec.toString());
            }
        } catch (IOException e) {
            log.error("cloneCommand ['{}'] -> error", dest, e);
            throw new RepositoryException("clone repository error: " + e.getMessage());
        }
    }

    private String processUrl(String url, Secret secret) {
        if (secret == null && url.trim().startsWith("https://") && cfg.oauthToken() != null && !url.contains("@")) {
            return "https://" + cfg.oauthToken() + "@" + url.substring("https://".length());
        }
        return url;
    }

    private void checkoutCommand(String ref, Path dest) {
        log.info("Checking out revision '{}'", ref);

        launchCommand(dest, defaultTimeout, "checkout", "-f", ref);
    }

    private void launchCommandWithCredentials(Path workDir,
                                              long timeout,
                                              List<String> args,
                                              Secret secret) {

        Path key = null;
        Path ssh = null;
        Path askpass = null;

        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0"); // Don't prompt for auth from command line git

        try {
            if (secret instanceof KeyPair) {
                KeyPair keyPair = (KeyPair) secret;

                key = createSshKeyFile(keyPair);
                ssh = createUnixGitSSH(key);

                env.put("GIT_SSH", ssh.toAbsolutePath().toString());
                env.put("GIT_SSH_COMMAND", ssh.toAbsolutePath().toString());

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
                env.put("SSH_ASKPASS", askpass.toAbsolutePath().toString());

                log.info("using GIT_ASKPASS to set credentials ");
            }

            env.put("GIT_HTTP_LOW_SPEED_LIMIT", String.valueOf(cfg.httpLowSpeedLimit()));
            env.put("GIT_HTTP_LOW_SPEED_TIME", String.valueOf(cfg.httpLowSpeedTime()));

            launchCommand(workDir, env, timeout, args);
        } catch (IOException e) {
            throw new RepositoryException("Failed to setup credentials", e);
        } finally {
            deleteTempFile(key);
            deleteTempFile(ssh);
            deleteTempFile(askpass);
        }
    }

    private String launchCommand(Path workDir,
                                 long timeout,
                                 String... args) throws RepositoryException {
        List<String> listArgs = Arrays.asList(args);
        Map<String, String> env = new HashMap<>();
        return launchCommand(workDir, env, timeout, listArgs);
    }

    private String launchCommand(Path workDir,
                                 Map<String, String> envVars,
                                 long timeout,
                                 List<String> args) {

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

        log.info("> {}", hideSensitiveData(String.join(" ", cmd)));

        try {
            Process p = pb.start();

            Future<StringBuilder> out = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("GIT: {}", hideSensitiveData(line));
                        sb.append(line).append("\n");
                    }
                }
                return sb;
            });

            Future<StringBuilder> error = executor.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb;
            });

            if (!p.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                p.destroy();
                throw new RepositoryException(String.format("Git operation timed out after %sms", timeout));
            }

            int code = p.exitValue();
            if (code != SUCCESS_EXIT_CODE) {
                String msg = "code: " + code + ", " + hideSensitiveData(error.get().toString());
                log.warn("launchCommand ['{}'] -> finished with code {}, error: '{}'",
                        hideSensitiveData(String.join(" ", cmd)), code, msg);
                throw new RepositoryException(msg);
            }

            return out.get().toString();
        } catch (ExecutionException | IOException | InterruptedException e) { // NOSONAR
            log.error("launchCommand ['{}'] -> error", hideSensitiveData(String.join(" ", cmd)), e);
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
            w.println("ssh -i \"" + key.toAbsolutePath().toString() + "\" -o ServerAliveCountMax=" + cfg.sshTimeoutRetryCount() +
                    " -o ServerAliveInterval=" + cfg.sshTimeout() +
                    " -o StrictHostKeyChecking=no \"$@\"");
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
            log.warn("can't delete tmp file: {}", tempFile);
        }
    }

    private String hideSensitiveData(String s) {
        if (s == null) {
            return null;
        }

        for (String p : sensitiveData) {
            s = s.replaceAll(p, "***");
        }
        return s;
    }
}
