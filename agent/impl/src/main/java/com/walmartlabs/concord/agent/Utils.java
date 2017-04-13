package com.walmartlabs.concord.agent;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class Utils {

    public static String getMainClass(Path workDir, String entryPoint) throws ExecutionException {
        Path p = workDir.resolve(entryPoint);
        try {
            JarFile jar = new JarFile(p.toFile());

            Manifest m = jar.getManifest();
            if (m == null) {
                throw new ExecutionException("Manifest not found: " + p);
            }

            Attributes attrs = m.getMainAttributes();
            String mainClass = attrs.getValue(Attributes.Name.MAIN_CLASS);
            if (mainClass == null) {
                throw new ExecutionException("Main-Class is not defined: " + p);
            }
            return mainClass;
        } catch (IOException e) {
            throw new ExecutionException("Error while opening a JAR file: " + p, e);
        }
    }

    public static ClassLoader createClassLoader(File baseDir) throws MalformedURLException {
        if (!baseDir.exists()) {
            // TODO replace with a system CL?
            return Utils.class.getClassLoader();
        }

        URL[] deps = collectDependencies(baseDir);
        return new URLClassLoader(deps);
    }

    private static URL[] collectDependencies(File baseDir) throws MalformedURLException {
        File libDir = new File(baseDir, "lib");
        if (!libDir.exists()) {
            return new URL[0];
        }

        Set<URL> deps = new HashSet<>();
        File[] fs = libDir.listFiles();
        if (fs == null) {
            return new URL[0];
        }

        for (File f : fs) {
            if (f.getName().endsWith(".jar")) {
                deps.add(f.toURI().toURL());
            }
        }
        return deps.toArray(new URL[deps.size()]);
    }

    private Utils() {
    }
}
