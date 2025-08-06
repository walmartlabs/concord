package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.maven.artifact.versioning.ComparableVersion;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "self-update", description = "Update the CLI to the latest release version")
public class SelfUpdate implements Callable<Integer> {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final URI GITHUB_RELEASES_ENDPOINT = URI.create("https://api.github.com/repos/walmartlabs/concord/releases/latest");
    private static final String DOWNLOAD_TEMPLATE = "https://repo.maven.apache.org/maven2/com/walmartlabs/concord/concord-cli/%1$s/concord-cli-%1$s.sh";

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display the command's help message")
    boolean helpRequested = false;

    @Override
    public Integer call() {
        var selfLocation = SelfUpdate.class.getProtectionDomain().getCodeSource().getLocation();

        Path dst;
        try {
            dst = Paths.get(selfLocation.getPath());
        } catch (InvalidPathException e) {
            return unableToDetermineSelfLocation();
        }

        if (Files.isDirectory(dst)) {
            return unableToDetermineSelfLocation();
        }
        if (!Files.isWritable(dst)) {
            return selfLocationIsNotWritable();
        }

        String latestVersion;
        try {
            System.out.println("Checking for updates...");
            var maybeLatestVersion = getLatestVersion();
            if (maybeLatestVersion.isEmpty()) {
                return unableToDetermineLatestReleaseVersion();
            }

            latestVersion = maybeLatestVersion.get();
        } catch (IOException | InterruptedException e) {
            return err(e.getMessage());
        }

        var currentVersion = Version.getVersion();
        var comparison = new ComparableVersion(latestVersion).compareTo(new ComparableVersion(currentVersion));
        if (comparison == 0) {
            return currentVersionIsLatest();
        } else if (comparison < 0) {
            return currentVersionIsMoreRecent();
        }

        System.out.printf("Updating to %s...%n", latestVersion);

        try {
            var tmpFile = Files.createTempFile("concord-cli-" + latestVersion, ".sh");
            var src = downloadArtifact(latestVersion, tmpFile);
            Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | InterruptedException e) {
            return err(e.getMessage());
        }

        System.out.println(ansi().fgBrightGreen().a("Done!"));

        try {
            var permissions = new HashSet<>(Files.getPosixFilePermissions(dst));
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(dst, permissions);
        } catch (UnsupportedOperationException | IOException e) {
            warn("Unable to mark the binary as an executable. You might need to manually update the permissions of " + dst.toAbsolutePath());
        }

        return 0;
    }

    private static Optional<String> getLatestVersion() throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        var request = HttpRequest.newBuilder()
                .uri(GITHUB_RELEASES_ENDPOINT)
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "concord-cli " + Version.getVersion())
                .GET()
                .build();

        var response = client.send(request, BodyHandlers.ofInputStream());
        if (response.statusCode() == 200) {
            var mapper = new ObjectMapper();
            try (var body = response.body()) {
                var json = mapper.readTree(body);
                var tagName = json.path("tag_name").asText();
                if (!tagName.isEmpty()) {
                    return Optional.of(tagName);
                }
            }
        } else if (response.statusCode() == 404) {
            throw new IOException("Repository not found or no releases available.");
        } else {
            throw new IOException("GitHub API returned unexpected status: " + response.statusCode());
        }
        return Optional.empty();
    }

    private static Path downloadArtifact(String version, Path dst) throws IOException, InterruptedException {
        var client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        var request = HttpRequest.newBuilder()
                .uri(URI.create(DOWNLOAD_TEMPLATE.formatted(version)))
                .timeout(TIMEOUT)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "concord-cli " + Version.getVersion())
                .GET()
                .build();

        var response = client.send(request, BodyHandlers.ofFile(dst));
        if (response.statusCode() == 200) {
            return response.body();
        } else if (response.statusCode() == 404) {
            throw new IOException("Release %s not found".formatted(version));
        } else {
            throw new IOException("Maven Central returned unexpected status: " + response.statusCode());
        }
    }

    private static int unableToDetermineSelfLocation() {
        return err("Unable to determine the location of the CLI binary, self-update is not possible.");
    }

    private static int selfLocationIsNotWritable() {
        return err("Unable to overwrite the CLI binary, self-update is not possible.");
    }

    private static int unableToDetermineLatestReleaseVersion() {
        return err("Cannot determine the latest release version.");
    }

    private static int currentVersionIsLatest() {
        System.out.println("The current version is the latest release version. Nothing to do.");
        return 0;
    }

    private static int currentVersionIsMoreRecent() {
        System.out.println("The current version is more recent than the latest available release version. Nothing to do.");
        return 0;
    }

    private static int err(String msg) {
        System.out.println(ansi().fgRed().a(msg));
        return -1;
    }

    private static void warn(String msg) {
        System.out.println(ansi().fgYellow().a(msg));
    }
}
