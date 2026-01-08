package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.runner.SerializationUtils;
import io.takari.bpm.event.Event;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.event.ExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FileEventStorage implements EventStorage {

    private static final Logger log = LoggerFactory.getLogger(FileEventStorage.class);

    private final Path dir;

    public FileEventStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void add(Event event) {
        Path p = dir.resolve(event.getId().toString());

        try {
            Path tmp = PathUtils.createTempFile(event.getId().toString(), "event");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                SerializationUtils.serialize(out, event);
            }
            Files.move(tmp, p, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("add ['{}', '{}'] -> done, {}", event.getProcessBusinessKey(), event.getName(), p);
    }

    @Override
    public Event get(UUID id) {
        Path p = dir.resolve(id.toString());
        if (!Files.exists(p)) {
            return null;
        }

        return get(p);
    }

    private Event get(Path p) {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(p))) {
            return (Event) in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Event remove(UUID id) {
        Event ev = get(id);
        if (ev == null) {
            return null;
        }

        Path p = dir.resolve(id.toString());
        try {
            Files.delete(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("remove ['{}', '{}'] -> done, {}", ev.getProcessBusinessKey(), ev.getName(), p);
        return ev;
    }

    @Override
    public Collection<Event> find(String processBusinessKey) {
        return find(processBusinessKey, null);
    }

    @Override
    public Collection<Event> find(String processBusinessKey, String eventName) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(this::get)
                    .filter(ev -> processBusinessKey.equals(ev.getProcessBusinessKey()) &&
                            (eventName == null || eventName.equals(ev.getName())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ExpiredEvent> findNextExpiredEvent(int maxEvents) {
        throw new RuntimeException("Not implemented");
    }
}
