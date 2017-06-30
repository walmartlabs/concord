package com.walmartlabs.concord.plugins.dynamic;

import com.walmartlabs.concord.common.DynamicTaskRegistry;
import com.walmartlabs.concord.common.Task;
import groovy.lang.GroovyClassLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

@Named("loadTasks")
public class LoadTasksTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(LoadTasksTask.class);

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

    private void register(Class c) throws Exception {
        taskRegistry.register((Task) c.newInstance());
        log.info("register ['{}'] -> done", c);
    }
}
