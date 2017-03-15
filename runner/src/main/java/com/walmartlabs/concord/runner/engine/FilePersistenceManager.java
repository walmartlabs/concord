package com.walmartlabs.concord.runner.engine;

import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.persistence.PersistenceManager;
import io.takari.bpm.state.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FilePersistenceManager implements PersistenceManager {

    private static final Logger log = LoggerFactory.getLogger(FilePersistenceManager.class);

    private final Path dir;

    public FilePersistenceManager(Path dir) {
        this.dir = dir;
    }

    @Override
    public void save(ProcessInstance state) throws ExecutionException {
        Path p = dir.resolve(state.getId().toString());

        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(p))) {
            out.writeObject(state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("save ['{}', '{}'] -> done, {}", state.getBusinessKey(), state.getId(), p);
    }

    @Override
    public ProcessInstance get(UUID id) {
        Path p = dir.resolve(id.toString());
        if (!Files.exists(p)) {
            return null;
        }

        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(p))) {
            return (ProcessInstance) in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove(UUID id) {
        Path p = dir.resolve(id.toString());
        if (!Files.exists(p)) {
            return;
        }

        try {
            Files.delete(p);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.debug("remove ['{}'] -> done, {}", id, p);
    }
}
