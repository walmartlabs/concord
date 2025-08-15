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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.walmartlabs.concord.cli.secrets.CliSecretService;
import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessApi;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.common.TemporaryPath;
import com.walmartlabs.concord.sdk.Constants;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.fusesource.jansi.Ansi.ansi;

@Command(name = "remote-run", description = "Execute flows remotely. Sends the specified <workDir> as the process payload.")
public class RemoteRun implements Callable<Integer> {

    private static final String[] PAYLOAD_ARCHIVE_FILTERS = {Pattern.quote(".concord"), Pattern.quote(".git")};
    private static final long LARGE_PAYLOAD_SIZE_BYTES = 16 * 1024 * 1024;
    private static final String LARGE_PAYLOAD_SIZE_HUMAN = "16MB";

    @Option(names = {"--context"}, description = "Configuration context to use")
    String context = "default";

    @Option(names = {"-v", "--verbose"}, description = "Use verbose output")
    boolean[] verbosity = new boolean[0];

    @Option(names = {"--profiles"}, description = "A comma-separated list of Concord profiles to use", split = ",")
    String[] activeProfiles;

    @Option(names = {"--org"}, description = "Start the process in the specified Concord organization")
    String orgName;

    @Option(names = {"--project"}, description = "Start the process in the specified Concord project")
    String projectName;

    @Option(names = {"--entry-point"}, description = "Name of the starting flow")
    String entryPoint;

    @Option(names = {"--args"}, description = "Process arguments (flow variables)")
    Map<String, String> processArgs = new LinkedHashMap<>();

    @Option(names = {"--cfg"}, description = "Process configuration in JSON format (dependencies, runtime, arguments, etc)")
    String processCfg;

    @Parameters(arity = "1", description = "A path to a single concord.yaml (or .yml) file or a directory with flows")
    Path workDir = Paths.get(System.getProperty("user.dir"));

    @Override
    public Integer call() {
        var verbosity = new Verbosity(this.verbosity);

        var mapper = new ObjectMapper();
        if (processCfg != null && !processCfg.isBlank()) {
            try {
                mapper.readValue(processCfg, ObjectNode.class);
            } catch (JsonProcessingException e) {
                return err("Expected a valid JSON object in <cfg>, got: " + processCfg);
            }
        }

        var configContext = CliConfig.load(verbosity, context, null);

        var remoteRunConfig = configContext.remoteRun();
        if (remoteRunConfig == null) {
            return missingConfig(context, "remoteRun");
        }
        if (remoteRunConfig.baseUrl() == null) {
            return missingConfig(context, "remoteRun.baseUrl");
        }
        if (remoteRunConfig.apiKeyRef() == null) {
            return missingConfig(context, "remoteRun.apiKeyRef");
        }

        var secretService = CliSecretService.create(configContext, workDir, verbosity);
        String apiKey;
        try {
            apiKey = secretService.exportAsString(remoteRunConfig.apiKeyRef().orgName(), remoteRunConfig.apiKeyRef().secretName(), null);
        } catch (Exception e) {
            return err("Unable to fetch the API key. " + e.getMessage());
        }

        var client = new ApiClient(HttpClient.newBuilder().build())
                .setBaseUrl(remoteRunConfig.baseUrl())
                .setApiKey(apiKey);

        var processApi = new ProcessApi(client);

        info("Preparing the payload...");

        if (!Files.exists(workDir)) {
            return err("<workDir> not found: " + workDir);
        }

        try {
            var payloadSize = getDirectorySize(workDir);
            if (payloadSize >= LARGE_PAYLOAD_SIZE_BYTES) {
                if (!Confirmation.confirm("The specified <workDir> is larger than %s. Continue? (y/N)".formatted(LARGE_PAYLOAD_SIZE_HUMAN))) {
                    return err("Aborting.");
                }
            }
        } catch (IOException e) {
            return err("Unable to determine the payload size: " + e.getMessage());
        }

        try (var archive = prepareArchive(workDir)) {
            var input = new HashMap<String, Object>();
            input.put("archive", archive.path());
            if (orgName != null) {
                input.put(Constants.Multipart.ORG_NAME, orgName);
            }
            if (projectName != null) {
                input.put(Constants.Multipart.PROJECT_NAME, projectName);
            }
            if (activeProfiles != null) {
                input.put(Constants.Request.ACTIVE_PROFILES_KEY, activeProfiles);
            }
            if (entryPoint != null) {
                input.put(Constants.Request.ENTRY_POINT_KEY, entryPoint);
            }
            if (processArgs != null) {
                processArgs.forEach((key, value) -> input.put(Constants.Request.ARGUMENTS_KEY + "." + key, value));
            }
            if (processCfg != null && !processCfg.isBlank()) {
                input.put("request", processCfg.getBytes(UTF_8));
            }

            info("Starting a new process...");
            var response = processApi.startProcess(input);
            info("Started %s/#/process/%s/log".formatted(remoteRunConfig.baseUrl(), response.getInstanceId()));
        } catch (ApiException e) {
            return handleApiException(e);
        } catch (IOException e) {
            return err("Failed to start a process. " + e.getMessage());
        }

        return 0;
    }

    private static TemporaryPath prepareArchive(Path src) throws IOException {
        var badSrc = false;

        if (Files.isRegularFile(src)) {
            var fileName = src.getFileName().toString();
            badSrc = !fileName.equals("concord.yml") && !fileName.equals("concord.yaml") && !fileName.endsWith(".yml") && !fileName.endsWith(".yaml");
        } else if (!Files.isDirectory(src)) {
            badSrc = true;
        }

        if (badSrc) {
            throw new IOException("Expected a path to a single concord.yaml (or .yml) file or a directory with flows, got " + src);
        }

        var dst = PathUtils.tempFile("payload", ".zip");

        try (var zip = new ZipArchiveOutputStream(dst.path(), StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            if (Files.isRegularFile(src)) {
                IOUtils.zipFile(zip, src, src.getFileName().toString());
            } else if (Files.isDirectory(src)) {
                IOUtils.zip(zip, src, PAYLOAD_ARCHIVE_FILTERS);
            }
        }

        return dst;
    }

    private static long getDirectorySize(Path src) throws IOException {
        try (var walker = Files.walk(src)) {
            return walker.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).sum();
        }
    }

    private static int handleApiException(ApiException apiException) {
        if (apiException.getCode() != 400) {
            return err("Failed to start a process. " + apiException.getMessage());
        }

        var contentType = apiException.getResponseHeaders().firstValue("Content-Type");
        if (contentType.filter(t -> t.contains("vnd.concord-validation-errors-v1+json")).isEmpty()) {
            return unexpectedErrorBody(apiException);
        }

        JsonNode validationErrors;
        try {
            var mapper = new ObjectMapper();
            validationErrors = mapper.readTree(apiException.getResponseBody());
        } catch (IOException e) {
            return unexpectedErrorBody(apiException);
        }

        if (validationErrors == null || !validationErrors.isArray()) {
            return unexpectedErrorBody(apiException);
        }

        var text = new StringBuilder();
        for (var node : validationErrors) {
            if (!node.isObject()) {
                return unexpectedErrorBody(apiException);
            }
            var message = node.get("message");
            if (message != null && !message.asText().isBlank()) {
                text.append(message.asText());
            }
        }

        validationErrors.forEach(e -> text.append(e.asText()).append(". "));
        return err("Failed to start a process. " + text);
    }

    private static void info(String msg) {
        System.out.println(msg);
    }

    private static int unexpectedErrorBody(ApiException e) {
        warn("Unable to parse the API error response body: " + e.getResponseBody());
        return err("Failed to start a process. " + e.getMessage());
    }

    private static int missingConfig(String context, String key) {
        return err("Missing '%s' configuration in the '%s' context".formatted(key, context));
    }

    private static void warn(String msg) {
        System.out.println(ansi().fgYellow().a(msg).reset());
    }

    private static int err(String msg) {
        System.out.println(ansi().fgRed().a(msg).reset());
        return -1;
    }
}
