package com.walmartlabs.concord.plugins.dynamic;

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.common.Task;
import groovy.lang.GroovyClassLoader;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Named("loadTasks")
public class LoadTasksTask implements Task {

    private final DynamicTaskRegistry taskRegistry;
    private final GroovyClassLoader classLoader;

    @Inject
    public LoadTasksTask(DynamicTaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
        this.classLoader = new GroovyClassLoader();
    }

    public void call(String path) throws Exception {
        Path p = Paths.get(path);
        Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    register(classLoader.parseClass(file.toFile()));
                } catch (Exception e) {
                    throw new RuntimeException("Error while loading a task: " + file, e);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void register(Class c) throws Exception {
        taskRegistry.register(c);
    }
}
