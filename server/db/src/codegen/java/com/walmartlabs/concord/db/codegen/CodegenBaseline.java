package com.walmartlabs.concord.db.codegen;

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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.postgresql.core.Parser;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Build-only schema baseline tooling; never included in the runtime artifact. */
public final class CodegenBaseline {
    private static final String REFRESH = "Codegen schema baseline is missing or stale. Run from the repository root: ./mvnw -pl server/db -am -Prefresh-codegen-baseline -DskipTests install";
    private static final String SOURCE = "src/codegen/java/com/walmartlabs/concord/db/codegen/CodegenBaseline.java";
    private static final List<String> DUMP_FLAGS = List.of("--schema-only", "--no-owner", "--no-privileges",
            "--exclude-table=public.databasechangelog", "--exclude-table=public.databasechangeloglock");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private final Properties properties;
    private final Path module;
    private final Path repository;
    private final Path work;
    private final boolean refresh;
    private final boolean verify;

    private CodegenBaseline(Properties properties) throws Exception {
        this.properties = properties;
        module = Path.of(required("codegen.moduleDirectory")).toRealPath();
        repository = module.getParent().getParent().toRealPath();
        work = Path.of(required("codegen.workDirectory")).toAbsolutePath();
        refresh = Boolean.parseBoolean(properties.getProperty("db.codegen.baseline.refresh", "false"));
        verify = Boolean.parseBoolean(properties.getProperty("db.codegen.baseline.verify", "false"));
        if (refresh && verify) {
            throw new IllegalArgumentException("Choose exactly one codegen baseline profile.");
        }
        if (refresh || verify) {
            if (Boolean.parseBoolean(properties.getProperty("db.codegen.external", "false"))) {
                throw new IllegalArgumentException("Baseline refresh/verification requires the module-managed PostgreSQL container; omit -Plooper.");
            }
            if (Boolean.parseBoolean(properties.getProperty("liquibase.skip", "false"))
                    || !Boolean.parseBoolean(properties.getProperty("liquibase.should.run", "true"))) {
                throw new IllegalArgumentException("Baseline refresh/verification requires Liquibase migrations; omit migration skip flags.");
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !Set.of("check", "run").contains(args[0]) || !Path.of(args[1]).isAbsolute()) {
            throw new IllegalArgumentException("Expected <check|run> <absolute build-properties-file>");
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(Path.of(args[1]))) {
            properties.load(input);
        }
        var baseline = new CodegenBaseline(properties);
        if (args[0].equals("check")) {
            baseline.check();
        } else {
            baseline.run();
        }
    }

    private void check() throws Exception {
        var fingerprint = fingerprint();
        if (!refresh) {
            artifact(fingerprint);
        }
        if (refresh || verify) {
            Files.createDirectories(work);
            Files.writeString(work.resolve("inputs.sha256"), fingerprint + "\n");
        }
    }

    private String required(String key) {
        var value = properties.getProperty(key);
        if (value == null || value.isBlank() || value.contains("${")) {
            throw new IllegalArgumentException("Missing or unresolved build property: " + key);
        }
        return value;
    }

    private String resolve(String text, Set<String> resolving) {
        var matcher = PROPERTY.matcher(text);
        var result = new StringBuilder();
        while (matcher.find()) {
            var key = matcher.group(1);
            if (!resolving.add(key)) {
                throw new IllegalArgumentException("Cyclic build property: " + key);
            }
            var value = properties.getProperty(key);
            if (value == null) {
                throw new IllegalArgumentException("Unresolved build property: " + key);
            }
            value = resolve(value, resolving);
            resolving.remove(key);
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        if (result.indexOf("${") >= 0) {
            throw new IllegalArgumentException("Malformed build property substitution");
        }
        return result.toString();
    }

    private String resolve(String text) {
        return resolve(text, new HashSet<>());
    }

    private static Element xml(Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static List<Element> children(Element element) {
        var result = new ArrayList<Element>();
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) {
                result.add(e);
            }
        }
        return result;
    }

    private static Element child(Element element, String name) {
        return children(element).stream().filter(e -> name.equals(e.getLocalName())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing XML element: " + name));
    }

    private String canonical(Element element, boolean configuration) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(output)) {
            field(data, "{" + element.getNamespaceURI() + "}" + element.getLocalName());
            var attributes = new TreeMap<String, String>();
            for (var i = 0; i < element.getAttributes().getLength(); i++) {
                var attribute = element.getAttributes().item(i);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                    attributes.put("{" + attribute.getNamespaceURI() + "}" + attribute.getLocalName(), stable(attribute.getNodeValue()));
                }
            }
            data.writeInt(attributes.size());
            for (var entry : attributes.entrySet()) {
                field(data, entry.getKey());
                field(data, entry.getValue());
            }
            for (var node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node instanceof Element e) {
                    if (!configuration || !Set.of("url", "username", "password").contains(e.getLocalName())) {
                        field(data, "element");
                        field(data, canonical(e, false));
                    }
                } else if ((node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                        && !node.getNodeValue().isBlank()) {
                    field(data, "text");
                    field(data, stable(node.getNodeValue().trim()));
                }
            }
        }
        return HexFormat.of().formatHex(output.toByteArray());
    }

    private String stable(String value) {
        return resolve(value).replace(module.toString() + "/", "").replace(module.toString(), ".");
    }

    private String fingerprint() throws Exception {
        try {
            var inputs = new TreeMap<String, byte[]>();
            var resources = module.resolve("src/main/resources").toRealPath();
            var plugin = children(child(child(xml(module.resolve("pom.xml")), "build"), "plugins")).stream()
                    .filter(e -> "plugin".equals(e.getLocalName()))
                    .filter(e -> children(e).stream().anyMatch(c -> "groupId".equals(c.getLocalName()) && "org.liquibase".equals(c.getTextContent().trim())))
                    .filter(e -> "liquibase-maven-plugin".equals(child(e, "artifactId").getTextContent().trim()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Missing canonical Liquibase plugin"));
            var configuration = child(plugin, "configuration");
            var changelog = module.resolve(resolve(child(configuration, "changeLogFile").getTextContent().trim())).normalize();
            requireInside(changelog, resources);
            if (!Files.isRegularFile(changelog) || !Files.isRegularFile(module.resolve(SOURCE))) {
                throw new IllegalArgumentException("Missing root changelog or codegen helper source.");
            }
            scan(inputs, resources, false);
            scan(inputs, repository.resolve("server/liquibase-ext/src/main"), true);
            scan(inputs, repository.resolve("common/src/main/java/com/walmartlabs/concord/common/secret"), true);
            scan(inputs, module.resolve("src/codegen/java"), true);
            try (var files = Files.walk(resources)) {
                for (var file : files.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".xml")).toList()) {
                    references(xml(file), file, resources);
                }
            }
            inputs.put("@liquibase.version", resolve(child(plugin, "version").getTextContent().trim()).getBytes(StandardCharsets.UTF_8));
            inputs.put("@liquibase.configuration", canonical(configuration, true).getBytes(StandardCharsets.UTF_8));
            inputs.put("@dump-format", ("format=1\n" + String.join("\n", DUMP_FLAGS) + "\n").getBytes(StandardCharsets.UTF_8));
            var digest = MessageDigest.getInstance("SHA-256");
            try (var stream = new DataOutputStream(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest))) {
                for (var input : inputs.entrySet()) {
                    field(stream, input.getKey());
                    stream.writeInt(input.getValue().length);
                    stream.write(input.getValue());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException(REFRESH, e);
        }
    }

    private static void field(DataOutputStream output, String value) throws Exception {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private void scan(Map<String, byte[]> inputs, Path root, boolean sources) throws Exception {
        requireInside(root, repository);
        try (var files = Files.walk(root)) {
            for (var file : files.toList()) {
                requireInside(file, repository);
                if (Files.isSymbolicLink(file) && Files.isDirectory(file)) {
                    throw new IllegalArgumentException("Symlinked input directory is unsupported: " + file);
                }
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                var name = repository.relativize(file).toString().replace('\\', '/');
                if (sources && !name.endsWith(".java") && !name.contains("/resources/")) {
                    continue;
                }
                var bytes = Files.readAllBytes(file);
                if (name.matches(".*\\.(java|xml|sql|properties)$") || name.contains("/META-INF/services/")) {
                    bytes = lf(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                }
                inputs.put(name, bytes);
            }
        }
    }

    private static void requireInside(Path file, Path root) throws Exception {
        if (!file.toRealPath().startsWith(root.toRealPath())) {
            throw new IllegalArgumentException("Unfingerprinted external input: " + file);
        }
    }

    private void references(Element element, Path file, Path resources) throws Exception {
        for (var name : List.of("file", "path", "valueBlobFile", "valueClobFile")) {
            if (!element.hasAttribute(name)) {
                continue;
            }
            var reference = resolve(element.getAttribute(name));
            var base = Boolean.parseBoolean(element.getAttribute("relativeToChangelogFile")) ? file.getParent() : resources;
            var target = base.resolve(reference).normalize();
            requireInside(target, resources);
        }
        for (var nested : children(element)) {
            references(nested, file, resources);
        }
    }

    private Properties artifact(String fingerprint) throws Exception {
        try {
            var manifest = new Properties();
            var manifestPath = module.resolve("src/codegen/schema.properties");
            var lines = Files.readAllLines(manifestPath);
            var keys = List.of("format", "inputs.sha256", "schema.sha256", "server.version.num", "pg_dump.version");
            if (lines.size() != keys.size()) {
                throw new IllegalArgumentException("Malformed manifest");
            }
            for (var i = 0; i < keys.size(); i++) {
                if (!lines.get(i).startsWith(keys.get(i) + "=")) {
                    throw new IllegalArgumentException("Malformed manifest");
                }
                manifest.setProperty(keys.get(i), lines.get(i).substring(keys.get(i).length() + 1));
            }
            var sql = lf(Files.readString(module.resolve("src/codegen/schema.sql")));
            if (sql.isBlank() || !"1".equals(manifest.getProperty("format"))
                    || !fingerprint.equals(manifest.getProperty("inputs.sha256"))
                    || !sha(sql).equals(manifest.getProperty("schema.sha256"))
                    || !manifest.getProperty("server.version.num").matches("[1-9][0-9]{5,}")
                    || Integer.parseInt(manifest.getProperty("server.version.num")) <= 0
                    || !manifest.getProperty("pg_dump.version").matches("[0-9]+(?:\\.[0-9]+)+")) {
                throw new IllegalArgumentException("Invalid baseline");
            }
            return manifest;
        } catch (Exception e) {
            throw new IllegalStateException(REFRESH, e);
        }
    }

    private static String lf(String text) {
        return text.replace("\r\n", "\n");
    }

    private static String sha(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    static String normalizeDump(String dump) {
        var chars = lf(dump).toCharArray();
        var result = new StringBuilder(chars.length);
        var start = 0;
        var sqlSeen = false;
        var ended = false;
        var token = "";
        for (var i = 0; i < chars.length; i++) {
            var c = chars[i];
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '-' && i + 1 < chars.length && chars[i + 1] == '-') {
                var end = Parser.parseLineComment(chars, i);
                var comment = new String(chars, i, Math.min(end + 1, chars.length) - i);
                if (!sqlSeen && (comment.startsWith("-- Dumped from database version ")
                        || comment.startsWith("-- Dumped by pg_dump version "))) {
                    result.append(chars, start, i - start);
                    start = Math.min(end + 1, chars.length);
                }
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < chars.length && chars[i + 1] == '*') {
                i = Parser.parseBlockComment(chars, i);
                continue;
            }
            if (c == '\\') {
                var end = i;
                while (end < chars.length && chars[end] != '\n') {
                    end++;
                }
                var command = new String(chars, i, end - i);
                if ((i != 0 && chars[i - 1] != '\n') || ended) {
                    throw new IllegalArgumentException("Misplaced psql command");
                }
                if (command.matches("\\\\restrict [A-Za-z0-9]+") && !sqlSeen && token.isEmpty()) {
                    token = command.substring("\\restrict ".length());
                } else if (!token.isEmpty() && sqlSeen && command.equals("\\unrestrict " + token)) {
                    ended = true;
                } else {
                    throw new IllegalArgumentException("Unsupported or mismatched psql command");
                }
                result.append(chars, start, i - start);
                start = end < chars.length ? end + 1 : end;
                i = start - 1;
                continue;
            }
            if (ended) {
                throw new IllegalArgumentException("SQL after dump trailer");
            }
            sqlSeen = true;
            i = switch (c) {
                case '\'' -> Parser.parseSingleQuotes(chars, i, true);
                case '"' -> Parser.parseDoubleQuotes(chars, i);
                case '$' -> Parser.parseDollarQuotes(chars, i);
                default -> i;
            };
        }
        if (!sqlSeen || (!token.isEmpty() && !ended)) {
            throw new IllegalArgumentException("Empty dump or unmatched psql wrapper");
        }
        result.append(chars, start, chars.length - start);
        while (!result.isEmpty() && result.charAt(result.length() - 1) == '\n') {
            result.setLength(result.length() - 1);
        }
        return result.append('\n').toString();
    }

    static void restoreSchema(Connection connection, String sql, int expectedServerVersion) throws SQLException {
        if (!connection.getAutoCommit()) {
            throw new SQLException("Codegen baseline requires a new autocommit connection.");
        }
        if (serverVersion(connection) != expectedServerVersion) {
            throw new SQLException("PostgreSQL server version differs from the baseline. " + REFRESH);
        }
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT EXISTS (SELECT FROM pg_catalog.pg_namespace WHERE nspname = 'public')
                       AND NOT EXISTS (SELECT FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public')
                       AND NOT EXISTS (SELECT FROM pg_catalog.pg_proc p JOIN pg_catalog.pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public')
                       AND NOT EXISTS (SELECT FROM pg_catalog.pg_type t JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace WHERE n.nspname = 'public')
                     """)) {
            result.next();
            if (!result.getBoolean(1)) {
                throw new SQLException("Codegen baseline requires an empty public schema; use a fresh disposable database.");
            }
        }
        sql = normalizeDump(sql);
        connection.setAutoCommit(false);
        var failure = (Throwable) null;
        try {
            try (var statement = connection.createStatement()) {
                statement.setEscapeProcessing(false);
                var result = statement.execute(sql);
                while (true) {
                    if (result) {
                        try (var rows = statement.getResultSet()) {
                            while (rows.next()) {
                                // Drain SELECT results in the native dump.
                            }
                        }
                    } else if (statement.getUpdateCount() == -1) {
                        break;
                    }
                    result = statement.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException | Error e) {
            failure = e;
            try {
                connection.rollback();
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException reset) {
                if (failure != null) {
                    failure.addSuppressed(reset);
                } else {
                    throw reset;
                }
            }
        }
    }

    private static int serverVersion(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SHOW server_version_num")) {
            result.next();
            return result.getInt(1);
        }
    }

    private void run() throws Exception {
        var fingerprint = fingerprint();
        var manifest = refresh ? null : artifact(fingerprint);
        if (!refresh && !verify) {
            var sql = Files.readString(module.resolve("src/codegen/schema.sql"));
            // Check the bytes actually being used, not just an earlier read of the artifact.
            if (!sha(lf(sql)).equals(manifest.getProperty("schema.sha256"))) {
                throw new IllegalStateException(REFRESH);
            }
            try (var connection = connect("postgres")) {
                restoreSchema(connection, sql, Integer.parseInt(manifest.getProperty("server.version.num")));
            }
            System.out.println("Codegen schema baseline restored.");
            return;
        }
        var initial = Files.readString(work.resolve("inputs.sha256")).trim();
        unchangedInputs(initial, fingerprint);
        var container = required("codegen.containerId");
        if (!container.matches("[0-9a-f]{12,64}")) {
            throw new IllegalStateException("Invalid Fabric8 container ID.");
        }
        var inspected = nativeTool(List.of("docker", "inspect", "--format", "{{.Id}}", container)).trim();
        if (!inspected.matches("[0-9a-f]{64}") || !inspected.startsWith(container)) {
            throw new IllegalStateException("Docker CLI must address the same daemon and module-managed container as Fabric8.");
        }
        container = inspected;
        var versionOutput = nativeTool(List.of("docker", "exec", container, "pg_dump", "--version")).trim();
        var versionMatch = Pattern.compile("^pg_dump \\(PostgreSQL\\) ([0-9]+(?:\\.[0-9]+)+)(?:\\s.*)?$").matcher(versionOutput);
        if (!versionMatch.matches()) {
            throw new IllegalStateException("Unrecognized pg_dump version: " + versionOutput);
        }
        var dumpVersion = versionMatch.group(1);
        try (var maintenance = connect("postgres")) {
            var version = serverVersion(maintenance);
            if (verify && version != Integer.parseInt(manifest.getProperty("server.version.num"))) {
                throw new IllegalStateException("PostgreSQL server version differs from the baseline. " + REFRESH);
            }
            var canonical = normalizeDump(dump(container, "postgres"));
            var candidate = refresh ? canonical : lf(Files.readString(module.resolve("src/codegen/schema.sql")));
            if (verify && !sha(candidate).equals(manifest.getProperty("schema.sha256"))) {
                throw new IllegalStateException(REFRESH);
            }
            var database = "concord_baseline_" + UUID.randomUUID().toString().replace("-", "");
            var created = false;
            var failure = (Exception) null;
            try {
                try (var statement = maintenance.createStatement()) {
                    statement.execute("CREATE DATABASE " + database);
                    created = true;
                }
                try (var connection = connect(database)) {
                    restoreSchema(connection, candidate, version);
                }
                var restored = normalizeDump(dump(container, database));
                if (!canonical.equals(restored)) {
                    Files.createDirectories(work);
                    var canonicalPath = work.resolve("canonical.sql");
                    var restoredPath = work.resolve("restored.sql");
                    Files.writeString(canonicalPath, canonical);
                    Files.writeString(restoredPath, restored);
                    throw new IllegalStateException("Schema parity failed: " + canonicalPath + " differs from " + restoredPath + ". " + REFRESH);
                }
            } catch (Exception e) {
                failure = e;
                throw e;
            } finally {
                if (created) {
                    try (var statement = maintenance.createStatement()) {
                        statement.execute("DROP DATABASE " + database);
                    } catch (SQLException cleanup) {
                        if (failure != null) {
                            failure.addSuppressed(cleanup);
                        } else {
                            throw cleanup;
                        }
                    }
                }
            }
            unchangedInputs(initial, fingerprint());
            if (refresh) {
                var text = "format=1\ninputs.sha256=" + initial + "\nschema.sha256=" + sha(candidate)
                        + "\nserver.version.num=" + version + "\npg_dump.version=" + dumpVersion + "\n";
                publish(module.resolve("src/codegen/schema.sql"), candidate);
                publish(module.resolve("src/codegen/schema.properties"), text);
                System.out.println("Codegen schema baseline refreshed after JDBC round-trip parity.");
            } else {
                System.out.println("Codegen schema baseline matches independent canonical migrations.");
            }
        }
    }

    private static void unchangedInputs(String initial, String current) {
        if (!initial.matches("[0-9a-f]{64}") || !initial.equals(current)) {
            throw new IllegalStateException("Codegen inputs changed during generation; rerun. " + REFRESH);
        }
    }

    private Connection connect(String database) throws SQLException {
        var settings = new Properties();
        settings.setProperty("user", required("db.username"));
        settings.setProperty("password", required("db.password"));
        return new org.postgresql.Driver().connect("jdbc:postgresql://" + required("db.host")
                + ":" + required("db.port") + "/" + database, settings);
    }

    private String dump(String container, String database) throws Exception {
        var command = new ArrayList<>(List.of("docker", "exec", container, "pg_dump",
                "-U", required("db.username"), "-d", database));
        command.addAll(DUMP_FLAGS);
        return nativeTool(command);
    }

    private String nativeTool(List<String> command) throws Exception {
        Files.createDirectories(work);
        var errors = Files.createTempFile(work, "native-", ".stderr");
        try {
            var process = new ProcessBuilder(command).redirectError(errors.toFile()).start();
            try (var stream = process.getInputStream()) {
                var output = stream.readAllBytes();
                if (process.waitFor() != 0) {
                    throw new IllegalStateException("Native baseline tool failed; Docker CLI must address the Fabric8 daemon: " + Files.readString(errors));
                }
                return new String(output, StandardCharsets.UTF_8);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Baseline refresh/verification requires Docker CLI pointed at the Fabric8 daemon.", e);
        } finally {
            Files.deleteIfExists(errors);
        }
    }

    private static void publish(Path destination, String text) throws Exception {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if (Files.exists(destination) && java.util.Arrays.equals(Files.readAllBytes(destination), bytes)) {
            return;
        }
        var staged = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        try {
            Files.write(staged, bytes);
            try {
                Files.move(staged, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }
}
