package com.walmartlabs.concord.agent.docker;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class OldImageSweeper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OldImageSweeper.class);

    private static final String[] LS_CMD = {"docker", "image", "ls", "--format", "{{.Repository}} {{.Tag}} {{.ID}}"};
    private static final String[] PS_CMD = {"docker", "ps", "-a", "--format", "{{.Image}}"};

    private static final long RETRY_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final long period;

    public OldImageSweeper(long period) {
        this.period = period;
    }

    @Override
    public void run() {
        log.info("run -> removing old Docker images...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Images images = findImages();
                Set<String> containers = findContainers();

                Set<String> oldIds = getOldIds(images, containers, 2);
                for (String imageId : oldIds) {
                    removeImage(imageId);
                }

                sleep(period);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("run -> error: {}, retrying in {}ms...", e.getMessage(), RETRY_DELAY);
                sleep(RETRY_DELAY);
            }
        }
    }

    private static Images findImages() throws IOException, InterruptedException {
        Images i = new Images();
        Utils.exec(LS_CMD, line -> {
            String[] as = line.split(" ");
            if (as.length != 3) {
                log.warn("findImages -> invalid line: {}", line);
                return;
            }

            i.addEntry(as[0], as[1], as[2]);
        });
        return i;
    }

    private static Set<String> findContainers() throws IOException, InterruptedException {
        Set<String> s = new HashSet<>();
        Utils.exec(PS_CMD, s::add);
        return s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Set<String> getOldIds(Images i, Set<String> containers, int keepNLastTags) {
        Set<String> ids = new HashSet<>();

        i.tags.forEach((repository, tags) -> {
            sortedTags(tags)
                    .skip(keepNLastTags)
                    .forEach(tag -> {
                        String c = repository + ":" + tag;
                        if (containers.contains(c)) {
                            // skip in-use images
                            return;
                        }

                        String id = i.ids.get(repository).get(tag);
                        ids.add(id);
                    });
        });

        return ids;
    }

    private static Stream<String> sortedTags(Set<String> tags) {
        if (tags.isEmpty()) {
            return Stream.empty();
        }

        List<String> l = new ArrayList<>(tags.size());

        if (tags.remove("latest")) {
            l.add("latest");
        }

        tags.stream()
                .sorted(Collections.reverseOrder())
                .forEach(l::add);

        return l.stream();
    }

    private static void removeImage(String imageId) throws IOException, InterruptedException {
        Process b = new ProcessBuilder()
                .command(createRmCommand(imageId))
                .start();

        int code = b.waitFor();
        if (code != 0) {
            log.warn("removeImage ['{}'] -> docker exit code {}, skipping...", imageId, code);
        } else {
            log.info("removeImage -> done, {} removed", imageId);
        }
    }

    private static String[] createRmCommand(String imageId) {
        return new String[]{"docker", "rmi", imageId};
    }

    private static final class Images {

        private final Map<String, Set<String>> tags = new HashMap<>();
        private final Map<String, Map<String, String>> ids = new HashMap<>();

        private void addEntry(String repository, String tag, String id) {
            tags.computeIfAbsent(repository, k -> new HashSet<>())
                    .add(tag);

            ids.computeIfAbsent(repository, k -> new HashMap<>())
                    .put(tag, id);
        }
    }

    public static void main(String[] args) throws Exception {
        new OldImageSweeper(10000).run();
    }
}
