package com.walmartlabs.concord.server.process.state;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.cfg.PayloadStoreConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Named
public class ProcessStateManagerImpl {

    private static final String WORKSPACE_DIR_NAME = "workspace";

    private final PayloadStoreConfiguration cfg;

    @Inject
    public ProcessStateManagerImpl(PayloadStoreConfiguration cfg) {
        this.cfg = cfg;
    }

    public void tx(TransactionRunnable c) {
        TransactionContext tx = new DummyTransactionContext();
        try {
            c.run(tx);
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    public <T> T txResult(TransactionCallable<T> c) {
        TransactionContext tx = new DummyTransactionContext();
        try {
            T result = c.run(tx);
            tx.commit();
            return result;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }

    public <T> Optional<T> get(String instanceId, String path, Function<InputStream, Optional<T>> converter) {
        Path p = resolve(instanceId, path);
        if (!Files.exists(p)) {
            return Optional.empty();
        }

        try (InputStream in = Files.newInputStream(p)) {
            return converter.apply(in);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> List<T> list(String instanceId, String path, Function<InputStream, Optional<T>> converter) {
        Path p = resolve(instanceId, path);
        if (!Files.exists(p)) {
            return Collections.emptyList();
        }

        try {
            return Files.list(p)
                    .map(new PathToEntityConverter<>(converter))
                    .flatMap(o -> o.isPresent() ? Stream.of(o.get()) : Stream.empty())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> Optional<T> findPath(String instanceId, String path, Function<Stream<String>, Optional<T>> converter) {
        Path p = resolve(instanceId, path);

        try {
            return converter.apply(Files.list(p).map(f -> f.toString()));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public boolean exists(String instanceId, String path) {
        Path p = resolve(instanceId, path);
        return Files.exists(p);
    }

    public boolean delete(String instanceId, String path) {
        return txResult(tx -> delete(tx, instanceId, path));
    }

    public boolean delete(TransactionContext tx, String instanceId, String path) {
        Path p = resolve(instanceId, path);
        try {
            return IOUtils.deleteRecursively(p);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public void importPath(String instanceId, String path, Path src) {
        tx(tx -> importPath(tx, instanceId, path, src));
    }

    public void importPath(TransactionContext tx, String instanceId, String path, Path src) {
        Path dst = resolve(instanceId, path);
        try {
            IOUtils.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public boolean exportPath(String instanceId, String path, BiConsumer<String, InputStream> consumer) {
        Path src = resolve(instanceId, path);
        if (!Files.exists(src)) {
            return false;
        }

        try {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = src.relativize(file).toString();
                    try (InputStream in = Files.newInputStream(file)) {
                        consumer.accept(name, in);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }

        return true;
    }

    private Path resolve(String instanceId, String s) {
        Path p = cfg.getBaseDir().resolve(instanceId)
                .resolve(WORKSPACE_DIR_NAME);

        if (s != null && !s.isEmpty()) {
            p = p.resolve(s);
        }

        return p;
    }

    public static BiConsumer<String, InputStream> saveAs(Path dst, OpenOption... options) {
        return new SaveConsumer(dst, options);
    }

    public static BiConsumer<String, InputStream> copyTo(Path dst, OpenOption... options) {
        return new CopyConsumer(dst, null, options);
    }

    public static BiConsumer<String, InputStream> copyTo(Path dst, String[] ignored, OpenOption... options) {
        return new CopyConsumer(dst, ignored, options);
    }

    public static BiConsumer<String, InputStream> zipTo(ZipOutputStream dst) {
        return new ZipConsumer(dst);
    }

    private static final class PathToEntityConverter<T> implements Function<Path, Optional<T>> {

        private final Function<InputStream, Optional<T>> delegate;

        private PathToEntityConverter(Function<InputStream, Optional<T>> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<T> apply(Path path) {
            try (InputStream in = Files.newInputStream(path)) {
                return delegate.apply(in);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    public interface TransactionRunnable {

        void run(TransactionContext tx);
    }

    public interface TransactionCallable<T> {

        T run(TransactionContext tx);
    }

    public interface TransactionContext {

        void commit();

        void rollback();
    }

    private static final class DummyTransactionContext implements TransactionContext {

        @Override
        public void commit() {
        }

        @Override
        public void rollback() {
        }
    }

    private static final class SaveConsumer implements BiConsumer<String, InputStream> {

        private final Path dst;
        private final OpenOption[] options;

        private SaveConsumer(Path dst, OpenOption[] options) {
            this.dst = dst;
            this.options = options;
        }

        @Override
        public void accept(String name, InputStream in) {
            try (OutputStream out = Files.newOutputStream(dst, options)) {
                IOUtils.copy(in, out);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static final class CopyConsumer implements BiConsumer<String, InputStream> {

        private final Path dst;
        private final String[] ignored;
        private final OpenOption[] options;

        private CopyConsumer(Path dst, String[] ignored, OpenOption[] options) {
            this.dst = dst;
            this.ignored = ignored;
            this.options = options;
        }

        @Override
        public void accept(String name, InputStream src) {
            if (ignored != null) {
                for (String i : ignored) {
                    if (name.matches(i)) {
                        return;
                    }
                }
            }

            Path p = dst.resolve(name);

            try {
                Path parent = p.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                try (OutputStream dst = Files.newOutputStream(p, options)) {
                    IOUtils.copy(src, dst);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static final class ZipConsumer implements BiConsumer<String, InputStream> {

        private final ZipOutputStream dst;

        private ZipConsumer(ZipOutputStream dst) {
            this.dst = dst;
        }

        @Override
        public void accept(String name, InputStream src) {
            try {
                dst.putNextEntry(new ZipEntry(name));
                IOUtils.copy(src, dst);
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }
}
