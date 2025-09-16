package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.common.ExpiringToken;
import com.walmartlabs.concord.common.AuthTokenProvider;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.Secret;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static com.walmartlabs.concord.common.LogUtils.withMdc;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;

public class GitClient {

    private static final Logger log = LoggerFactory.getLogger(GitClient.class);

    private static final int OBJECT_ID_LENGTH = 20;
    private static final int OBJECT_ID_STRING_LENGTH = OBJECT_ID_LENGTH * 2;

    private static final int SUCCESS_EXIT_CODE = 0;

    private final GitClientConfiguration cfg;
    private final AuthTokenProvider authProvider;

    private final Set<Obfuscation> sensitiveData;
    private final ExecutorService executor;

    public GitClient(GitClientConfiguration cfg, AuthTokenProvider authProvider) {
        this.cfg = cfg;
        this.authProvider = authProvider;
        this.executor = Executors.newCachedThreadPool();
        this.sensitiveData = new LinkedHashSet<>();

        sensitiveData.add(new Obfuscation("(https://[^:]*:)[^@]+(@)", "$1***$2"));
        sensitiveData.add(new Obfuscation("(https://x-access-token:)[^@]+(@)", "$1***$2"));

        if (cfg.oauthToken() != null) {
            sensitiveData.add(new Obfuscation(cfg.oauthToken(), "***"));
        }
    }

    public FetchResult fetch(FetchRequest req) {
        assertSecret(req.url(), req.secret());

        try {
            boolean exists = Files.exists(req.destination().resolve(".git"));
            if (!exists) {
                Files.createDirectories(req.destination());

                init(req.destination());
            }

            configure(req.destination());
            configureRemote(req.destination(), updateUrl(req.url(), req.secret()));

            Ref ref = getHeadRef(req.destination(), req.version().ref(), req.secret());
            configureFetch(req.destination(), getRefSpec(ref));

            NormalizedVersion version = NormalizedVersion.from(req.version(), ref);

            // fetch
            boolean alreadyFetched = exists && req.checkAlreadyFetched() && alreadyFetched(req.destination(), ref, version);
            if (!alreadyFetched) {
                boolean effectiveShallow = req.shallow() && version.commitId() == null;
                fetch(req.destination(), effectiveShallow, req.secret());

                checkout(req.destination(), req.version().value());
                if (version.commitId() == null) {
                    reset(req.destination(), ref.tag() ? "origin/tags/" + ref.name() : "origin/" + ref.name());
                }

                cleanup(req.destination());

                if (req.includeSubmodules() && hasSubmodules(req.destination())) {
                    updateSubmodules(req.destination(), req.secret());
                    resetSubmodules(req.destination());
                }
            }

            ImmutableFetchResult.Builder result = FetchResult.builder()
                    .head(revParse(req.destination(), "HEAD"))
                    .branchOrTag(ref != null ? ref.name() : null);

            if (req.withCommitInfo()) {
                CommitInfo ci = getCommitInfo(req.destination());
                result.message(ci.message())
                        .author(ci.author());
            }

            return result.build();
        } catch (RepositoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("fetch ['{}'] -> error", req, e);
            throw new RepositoryException("Error while fetching a repository: " + e.getMessage());
        }
    }

    record Obfuscation(String pattern, String replacement) {}

    private boolean alreadyFetched(Path workDir, Ref ref, NormalizedVersion version) {
        String head = revParse(workDir, "HEAD");

        if (version.commitId() != null) {
            return head.equalsIgnoreCase(version.commitId());
        }

        if (ref == null) {
            return false;
        }

        return ref.commitId().equals(head);
    }

    private void init(Path workDir) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("init")
                .build());
    }

    private void configure(Path workDir) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "advice.detachedHead", "false")
                .build());
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "maintenance.auto", "false")
                .build());
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "gc.auto", "0")
                .build());
    }

    private void configureRemote(Path workDir, String url) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "remote.origin.url", url)
                .build());
    }

    private void configureFetch(Path workDir, String refSpec) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "--replace-all", "remote.origin.fetch", refSpec)
                .build());
    }

    private void fetch(Path workDir, boolean shallow, Secret secret) {
        List<String> args = new ArrayList<>();
        args.add("fetch");
        if (shallow) {
            args.add("--depth=1");
        } else if (isShallowRepo(workDir)){
            args.add("--unshallow");
        }
        args.add("--quiet");
        args.add("origin");

        execWithCredentials(Command.builder()
                .workDir(workDir)
                .timeout(cfg.fetchTimeout())
                .addAllArgs(args)
                .build(), secret);
    }

    private boolean isShallowRepo(Path workDir) {
        String result = exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("rev-parse", "--is-shallow-repository")
                .build());

        return Boolean.parseBoolean(result.trim());
    }

    private void checkout(Path workDir, String rev) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("checkout", "-q", "-f", rev)
                .build());
    }

    private void reset(Path workDir, String rev) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("reset", "--hard", rev)
                .build());
    }

    private void cleanup(Path workDir) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("clean", "-fdx")
                .build());
    }

    private String revParse(Path workDir, String rev) {
        String result = exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("rev-parse", rev)
                .build());
        String line = result.trim();
        if (line.isEmpty()) {
            throw new RepositoryException("rev-parse no content returned for '" + rev + "'");
        }
        return fromString(line);
    }

    private List<Ref> getRefs(Path workDir, String version, Secret secret) {
        String result = execWithCredentials(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("ls-remote", "--symref", "origin", version)
                .build(), secret);

        List<Ref> refs = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(result))) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }

                String[] commitRef = line.split("\t");
                if (commitRef.length != 2) {
                    throw new RepositoryException("invalid response: " + result);
                }

                refs.add(Ref.builder()
                        .commitId(commitRef[0].trim())
                        .ref(commitRef[1].trim())
                        .name(version)
                        .build());
            }
        } catch (IOException e) {
            throw new RepositoryException("Error parsing result: " + result, e);
        }

        return refs;
    }

    private Ref getHeadRef(Path workDir, String version, Secret secret) {
        if (version == null) {
            return null;
        }

        String branchHeadRef = "refs/heads/" + version;
        String tagRef = "refs/tags/" + version;
        return getRefs(workDir, version, secret).stream()
                .filter(r -> r.ref().equalsIgnoreCase(branchHeadRef) || r.ref().equalsIgnoreCase(tagRef))
                .findFirst()
                .orElse(null);
    }

    private static String getRefSpec(Ref ref) {
        if (ref == null) {
            return "+refs/heads/*:refs/remotes/origin/*";
        }
        if (ref.tag()) {
            return String.format("+refs/tags/%s:refs/remotes/origin/tags/%s", ref.name(), ref.name());
        } else {
            return String.format("+refs/heads/%s:refs/remotes/origin/%s", ref.name(), ref.name());
        }
    }

    private String updateUrl(String url, Secret secret) {
        url = url.trim();

        if (!url.matches("^[A-Za-z][A-Za-z0-9+.-]+://.*")) {
            // no scheme. Assume ssh, e.g. from GitHub like 'git@github.com:owner/repo.git'
            assertUriAllowed("ssh://" + url);

            return url; // return un-modified. git cli doesn't want the ssh scheme
        }

        URI uri = assertUriAllowed(url);

        if (secret == null && !authProvider.supports(uri, null)) {
            // no user-provided auth
            // no matching system-provided auth
            return url;
        }

        if (url.contains("@") || !url.startsWith("https://")) {
            // provided url already has credentials OR it's a non-https url
            return url;
        }

        if (secret instanceof UsernamePassword up) {
            // Secret contains static auth (token or username). No lookup needed.
            return "https://" +
                    (up.getUsername() == null ? "" : up.getUsername() + ":") +
                    String.valueOf(up.getPassword()) +
                    "@" +
                    url.substring("https://".length());
        } else if (!authProvider.supports(uri, secret)) {
            // not compatible with auth provider
            return url;
        }

        // TODO move to method in provider interface. e.g. .updateGitHttpUrl, so username can be handled if it matters for non-github hosts
        String token = authProvider.getToken(uri, secret)
                .map(ExpiringToken::token)
                .orElse(null);

        if (token == null) {
            // provided url already has credentials OR there are no default credentials to use.
            // anonymous auth is the only viable option.
            return url;
        }

        return "https://x-access-token:" + token + "@" + url.substring("https://".length());
    }

    private void updateSubmodules(Path workDir, Secret secret) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("submodule", "init")
                .build());
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("submodule", "sync")
                .build());

        String[] modulePaths;

        try {
            modulePaths = exec(
                    Command.builder()
                            .workDir(workDir)
                            .timeout(cfg.defaultOperationTimeout())
                            .addArgs("config", "--file", ".gitmodules", "--name-only", "--get-regexp", "path")
                    .build())
                    .split("\\r?\\n");
        } catch (RepositoryException e) {
            log.warn("updateSubmodules ['{}'] -> error while retrieving the list of submodules: {}", workDir, e.getMessage());
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("submodule");
        args.add("update");

        args.add("--init");
        args.add("--recursive");

        for (String mp : modulePaths) {
            if (mp.trim().isEmpty()) {
                continue;
            }

            String moduleName = mp.substring("submodule.".length(), mp.length() - ".path".length());

            String url = getSubmoduleUrl(workDir, moduleName);
            if (url == null) {
                throw new RepositoryException("Empty repository for " + moduleName);
            }

            String pUrl = updateUrl(url, secret);
            if (!pUrl.equals(url)) {
                exec(Command.builder()
                        .workDir(workDir)
                        .timeout(cfg.defaultOperationTimeout())
                        .addArgs("config", "submodule." + moduleName + ".url", pUrl)
                        .build());
            }

            String sModulePath = getSubmodulePath(workDir, moduleName);

            List<String> perModuleArgs = new ArrayList<>(args);
            perModuleArgs.add(sModulePath);
            execWithCredentials(Command.builder()
                            .workDir(workDir)
                            .timeout(cfg.fetchTimeout())
                            .addAllArgs(perModuleArgs)
                            .build(),
                    secret);
        }
    }

    private void resetSubmodules(Path workDir) {
        exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("submodule", "foreach", "git", "reset", "--hard")
                .build());
    }

    private String getSubmoduleUrl(Path workDir, String name) {
        String result = exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "-f", ".gitmodules", "--get", "submodule." + name + ".url")
                .build());
        String s = firstLine(result);
        return s != null ? s.trim() : null;
    }

    private String getSubmodulePath(Path workDir, String name) {
        String result = exec(Command.builder()
                .workDir(workDir)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("config", "-f", ".gitmodules", "--get", "submodule." + name + ".path")
                .build());
        String s = firstLine(result);
        return s != null ? s.trim() : null;
    }

    private CommitInfo getCommitInfo(Path path) {
        String result = exec(Command.builder()
                .workDir(path)
                .timeout(cfg.defaultOperationTimeout())
                .addArgs("log", "-1", "--format=%an (%ae)%n%s%n%b")
                .build());
        String[] info = result.split("\n");
        if (info.length < 1) {
            return CommitInfo.builder().build();
        }
        String author = info[0];
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < info.length; i++) {
            message.append(info[i]).append("\n");
        }

        return CommitInfo.builder()
                .message(message.toString())
                .author(author)
                .build();
    }

    private String exec(Command command) {
        List<String> cmd = new ArrayList<>(command.args().size() + 1);
        cmd.add("git");
        cmd.addAll(command.args());

        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(command.workDir().toFile());

        Map<String, String> env = pb.environment();
        env.putAll(command.env());

        // Prevent interactive credential input.
        if (!env.containsKey("GIT_ASKPASS")) {
            env.put("GIT_ASKPASS", "echo");
        }

        log.info("> {}", hideSensitiveData(String.join(" ", cmd)));

        try {
            Process p = pb.start();

            Future<StringBuilder> out = executor.submit(withMdc(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("GIT (stdout): {}", hideSensitiveData(line));
                        sb.append(line).append("\n");
                    }
                }
                return sb;
            }));

            Future<StringBuilder> error = executor.submit(withMdc(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        log.info("GIT (stderr): {}", hideSensitiveData(line));
                        sb.append(line).append("\n");
                    }
                }
                return sb;
            }));

            if (!p.waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                p.destroy();
                throw new RepositoryException(String.format("git operation timed out after %sms", command.timeout()));
            }

            int code = p.exitValue();
            if (code != SUCCESS_EXIT_CODE) {
                String msg = String.format("code: %d, %s", code, hideSensitiveData(error.get().toString()));
                log.warn("exec ['{}'] -> finished with code {}, error: '{}'",
                        hideSensitiveData(String.join(" ", cmd)), code, msg);
                throw new RepositoryException(msg);
            }

            return out.get().toString();
        } catch (ExecutionException | IOException | InterruptedException e) { // NOSONAR
            log.error("exec ['{}'] -> error", hideSensitiveData(String.join(" ", cmd)), e);
            throw new RepositoryException("git operation error: " + e.getMessage());
        }
    }

    private URI assertUriAllowed(String rawUri) {
        // make sure it's in valid format
        URI uri = URI.create(rawUri);
        assertUriAllowed(uri);

        return uri;
    }

    //TODO make sure that if allowedSchemes = [ "https", "ssh", "git", "file" ] is not present, allow all schemes
    private void assertUriAllowed(URI uri) {
        String providedScheme = uri.getScheme();
        Set<String> allowedSchemes = cfg.allowedSchemes();
        boolean hasScheme = providedScheme != null && (!providedScheme.isEmpty());

        if (allowedSchemes.isEmpty()) {
            return; // allow all
        }

        if (allowedSchemes.isEmpty()) {
            return;
        }

        // the provided repo string is definitely an allowed protocol.
        if (hasScheme && allowedSchemes.contains(providedScheme)) {
            return;
        }

        // the provided repo string has no explicit scheme, should be understood to use an ssh connection
        if (!hasScheme && uri.getUserInfo() != null) {
            return;
        }

        String msg = String.format("Provided repository ('%s') contains an unsupported URI scheme: '%s'.",
                uri, uri.getScheme());
        log.warn(msg);
        throw new RepositoryException(msg);
    }

    private String execWithCredentials(Command cmd, Secret secret) {
        Path key = null;
        Path ssh = null;
        Path askpass = null;

        Map<String, String> env = new HashMap<>();
        env.put("GIT_TERMINAL_PROMPT", "0");

        try {
            if (secret instanceof KeyPair keyPair) {
                key = createSshKeyFile(keyPair);
                ssh = createUnixGitSSH(key);

                env.put("GIT_SSH", ssh.toAbsolutePath().toString());
                env.put("GIT_SSH_COMMAND", ssh.toAbsolutePath().toString());

                // supply a dummy value for DISPLAY so ssh will invoke SSH_ASKPASS
                if (!env.containsKey("DISPLAY")) {
                    env.put("DISPLAY", ":");
                }

                log.info("using GIT_SSH to set credentials");
            } else if (secret instanceof UsernamePassword userPass) {
                askpass = createUnixStandardAskpass(userPass);

                env.put("GIT_ASKPASS", askpass.toAbsolutePath().toString());
                env.put("SSH_ASKPASS", askpass.toAbsolutePath().toString());

                log.info("using GIT_ASKPASS to set credentials ");
            } else if (secret instanceof BinaryDataSecret token) {
                askpass = createUnixStandardAskpass(new UsernamePassword(new String(token.getData()), "".toCharArray()));

                env.put("GIT_ASKPASS", askpass.toAbsolutePath().toString());

                log.info("using GIT_ASKPASS to set credentials ");
            }

            env.put("GIT_HTTP_LOW_SPEED_LIMIT", String.valueOf(cfg.httpLowSpeedLimit()));
            env.put("GIT_HTTP_LOW_SPEED_TIME", String.valueOf(cfg.httpLowSpeedTime().getSeconds()));

            return exec(Command.builder().from(cmd)
                    .putAllEnv(env)
                    .build());
        } catch (IOException e) {
            throw new RepositoryException("Failed to setup credentials", e);
        } finally {
            deleteTempFile(key);
            deleteTempFile(ssh);
            deleteTempFile(askpass);
        }
    }

    private String hideSensitiveData(String s) {
        if (s == null) {
            return null;
        }

        for (Obfuscation o : sensitiveData) {
            s = s.replaceAll(o.pattern(), o.replacement());
        }
        return s;
    }

    private Path createUnixGitSSH(Path key) throws IOException {
        Path ssh = PathUtils.createTempFile("ssh", ".sh");

        try (PrintWriter w = new PrintWriter(ssh.toFile(), Charset.defaultCharset().toString())) {
            w.println("#!/bin/sh");
            // ${SSH_ASKPASS} might be ignored if ${DISPLAY} is not set
            w.println("if [ -z \"${DISPLAY}\" ]; then");
            w.println("  DISPLAY=:123.456");
            w.println("  export DISPLAY");
            w.println("fi");
            w.println("ssh -i \"" + key.toAbsolutePath().toString() + "\" -o ServerAliveCountMax=" + cfg.sshTimeoutRetryCount() +
                    " -o ServerAliveInterval=" + cfg.sshTimeout().getSeconds() +
                    " -o StrictHostKeyChecking=no \"$@\"");
        }
        Files.setPosixFilePermissions(ssh, Set.of(OWNER_READ, OWNER_EXECUTE));
        return ssh;
    }

    private static void assertSecret(String url, Secret secret) {
        if (secret instanceof BinaryDataSecret && !url.trim().startsWith("https://")) {
            throw new RepositoryException("Tokens can only be used for https:// Git URLs");
        }
    }

    private static Path createUnixStandardAskpass(UsernamePassword creds) throws IOException {
        Path askpass = PathUtils.createTempFile("pass", ".sh");
        try (PrintWriter w = new PrintWriter(askpass.toFile(), Charset.defaultCharset().toString())) {
            w.println("#!/bin/sh");
            w.println("case \"$1\" in");
            w.println("Username*) echo '" + quoteUnixCredentials(creds.getUsername()) + "' ;;");
            w.println("Password*) echo '" + quoteUnixCredentials(new String(creds.getPassword())) + "' ;;");
            w.println("esac");
        }
        Files.setPosixFilePermissions(askpass, Set.of(OWNER_READ, OWNER_EXECUTE));
        return askpass;
    }

    private static Path createSshKeyFile(KeyPair keyPair) throws IOException {
        Path keyFile = PathUtils.createTempFile("ssh", ".key");

        Files.write(keyFile, keyPair.getPrivateKey());

        return keyFile;
    }

    private static void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.delete(tempFile);
        } catch (IOException e) {
            log.warn("can't delete tmp file: {}", tempFile);
        }
    }

    private static String quoteUnixCredentials(String str) {
        // Assumes string will be used inside of single quotes, as it will
        // only replace "'" substrings.
        return str.replace("'", "'\\''");
    }

    private static boolean hasSubmodules(Path workDir) {
        return Files.exists(workDir.resolve(".gitmodules"));
    }

    private static String firstLine(String result) {
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

    private static String fromString(String str) {
        if (str.length() != OBJECT_ID_STRING_LENGTH) {
            throw new RuntimeException("Invalid object id:" + str);
        }
        return str;
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface Command {

        Path workDir();

        @Value.Default
        default List<String> args() {
            return Collections.emptyList();
        }

        @Value.Default
        default Map<String, String> env() {
            return Collections.emptyMap();
        }

        Duration timeout();

        static ImmutableCommand.Builder builder() {
            return ImmutableCommand.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface Ref {

        String commitId();

        String ref();

        String name();

        default boolean tag() {
            return ref().equals("refs/tags/" + name());
        }

        static ImmutableRef.Builder builder() {
            return ImmutableRef.builder();
        }
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface NormalizedVersion {

        static NormalizedVersion from(FetchRequest.Version version, Ref ref) {
            boolean versionRefIsBranchOrTag = ref != null;
            boolean versionValueIsBranchOrTag = versionRefIsBranchOrTag && version.value().equals(version.ref());
            String commitId = versionValueIsBranchOrTag ? null : version.value();
            return ImmutableNormalizedVersion.builder()
                    .commitId(commitId)
                    .branchOrTag(version.ref())
                    .build();
        }

        @Nullable
        String commitId();

        @Nullable
        String branchOrTag();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface CommitInfo {

        @Nullable
        String message();

        @Nullable
        String author();

        static ImmutableCommitInfo.Builder builder() {
            return ImmutableCommitInfo.builder();
        }
    }
}
