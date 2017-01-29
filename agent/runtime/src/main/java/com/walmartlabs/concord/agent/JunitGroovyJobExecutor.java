package com.walmartlabs.concord.agent;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovySystem;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Path;

// TODO redo as a subclass of JarJobExecutor
public class JunitGroovyJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JunitGroovyJobExecutor.class);

    @Override
    public void exec(String id, Path workDir, String entryPoint) throws ExecutionException {
        File tmpDir = workDir.toFile();

        ClassLoader deps;
        try {
            deps = Utils.createClassLoader(workDir.resolve("lib").toFile());
        } catch (MalformedURLException e) {
            throw new ExecutionException("Error while collecting dependencies", e);
        }

        GroovyClassLoader gcl = null;
        try {
            gcl = new GroovyClassLoader(deps);
            GroovySystem.getMetaClassRegistry().setMetaClass(File.class, new FileMetaClass(tmpDir));

            InputStream src = new FileInputStream(new File(tmpDir, entryPoint));
            GroovyCodeSource code = new GroovyCodeSource(new InputStreamReader(src), entryPoint, tmpDir.getAbsolutePath());
            Class<?> test = gcl.parseClass(code, false);

            JUnitCore junit = new JUnitCore();
            Result r = junit.run(test);

            if (r.getFailureCount() > 0) {
                for (Failure f : r.getFailures()) {
                    throw new Exception(f.toString());
                }
            }
        } catch (Exception e) {
            throw new ExecutionException("Error while executing a groovy script", e);
        } finally {
            if (gcl != null) {
                try {
                    gcl.close();
                } catch (IOException e) {
                    log.warn("Problem closing a groovy classloader", e);
                }

                for (Class<?> c : gcl.getLoadedClasses()) {
                    GroovySystem.getMetaClassRegistry().removeMetaClass(c);
                }
            }
        }
    }
}
