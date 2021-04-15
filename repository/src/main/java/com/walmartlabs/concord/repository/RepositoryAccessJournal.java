package com.walmartlabs.concord.repository;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RepositoryAccessJournal {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAccessJournal.class);

    private final Path repoJournalPath;
    private final ObjectMapper objectMapper;
    private final Map<String, RepositoryJournalItem> journal;

    public RepositoryAccessJournal(ObjectMapper objectMapper, Path repoJournalPath) throws IOException {
        this.objectMapper = objectMapper;

        if (Files.notExists(repoJournalPath)) {
            Files.createDirectories(repoJournalPath);
        }
        this.repoJournalPath = repoJournalPath;
        this.journal = load(repoJournalPath);
    }

    public void recordAccess(String repoUrl, Path repoLocalPath) throws IOException {
        RepositoryJournalItem item = RepositoryJournalItem.builder()
                .repoUrl(repoUrl)
                .repoPath(repoLocalPath)
                .lastAccess(System.currentTimeMillis())
                .build();
        journal.put(repoUrl, item);

        objectMapper.writeValue(repoJournalPath(repoUrl).toFile(), item);
    }

    public void removeRecord(String repoUrl) throws IOException {
        Files.deleteIfExists(repoJournalPath(repoUrl));
        journal.remove(repoUrl);
    }

    public List<RepositoryJournalItem> listOld(long age) {
        long now = System.currentTimeMillis();
        return journal.values().stream()
                .filter(j -> j.lastAccess() + age < now)
                .collect(Collectors.toList());
    }

    private RepositoryJournalItem loadItem(Path p) {
        try {
            return objectMapper.readValue(p.toFile(), RepositoryJournalItem.class);
        } catch (Exception e) {
            log.warn("loadItem ['{}'] -> error", p, e);
            return null;
        }
    }

    private Map<String, RepositoryJournalItem> load(Path repoJournalPath) throws IOException {
        Map<String, RepositoryJournalItem> result = new ConcurrentHashMap<>();
        try (Stream<Path> paths = Files.walk(repoJournalPath, 1, FileVisitOption.FOLLOW_LINKS)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".info.json"))
                    .map(this::loadItem)
                    .filter(Objects::nonNull)
                    .forEach(i -> result.put(i.repoUrl(), i));
        }
        return result;
    }

    private Path repoJournalPath(String repoUrl) {
        return repoJournalPath.resolve(encodeUrl(repoUrl) + ".info.json");
    }

    private static String encodeUrl(String url) {
        String encodedUrl;
        try {
            encodedUrl = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RepositoryException("Url encoding error", e);
        }

        return encodedUrl;
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableRepositoryJournalItem.class)
    @JsonDeserialize(as = ImmutableRepositoryJournalItem.class)
    interface RepositoryJournalItem {

        String repoUrl();

        Path repoPath();

        long lastAccess();

        static ImmutableRepositoryJournalItem.Builder builder() {
            return ImmutableRepositoryJournalItem.builder();
        }
    }
}
