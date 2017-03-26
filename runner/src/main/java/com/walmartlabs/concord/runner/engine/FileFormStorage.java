package com.walmartlabs.concord.runner.engine;

import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormStorage;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FileFormStorage implements FormStorage {

    private final Path dir;

    public FileFormStorage(Path dir) {
        this.dir = dir;
    }

    @Override
    public void save(Form form) throws ExecutionException {
        UUID id = form.getFormInstanceId();

        Path p = dir.resolve(id.toString());
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(p))) {
            out.writeObject(form);
        } catch (IOException e) {
            throw new ExecutionException("Error while saving a form", e);
        }
    }

    @Override
    public void complete(UUID formInstanceId) throws ExecutionException {
        throw new IllegalStateException("Shouldn't be called from the runner's side");
    }

    @Override
    public Form get(UUID formInstanceId) throws ExecutionException {
        Path p = dir.resolve(formInstanceId.toString());
        if (!Files.exists(p)) {
            return null;
        }

        try (ObjectInputStream out = new ObjectInputStream(Files.newInputStream(p))) {
            return (Form) out.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new ExecutionException("Error while reading a form", e);
        }
    }
}
