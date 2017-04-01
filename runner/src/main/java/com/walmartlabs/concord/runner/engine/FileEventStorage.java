package com.walmartlabs.concord.runner.engine;

import io.takari.bpm.event.Event;
import io.takari.bpm.event.EventStorage;
import io.takari.bpm.event.ExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class FileEventStorage implements EventStorage {

    private static final Logger log = LoggerFactory.getLogger(FileEventStorage.class);

    private final Path dir;

    public FileEventStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void add(Event event) {
        Path p = dir.resolve(event.getId().toString());

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(p))) {
            out.writeObject(event);
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
        try {
            return Files.list(dir)
                    .map(this::get)
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
