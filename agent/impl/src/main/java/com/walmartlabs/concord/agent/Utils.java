package com.walmartlabs.concord.agent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class Utils {

    public static String getMainClass(Path workDir, String jarFile) throws ExecutionException {
        Path p = workDir.resolve(jarFile);
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

    public static boolean kill(Process proc) {
        if (!proc.isAlive()) {
            return false;
        }

        proc.destroy();

        if (proc.isAlive()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        while (proc.isAlive()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // ignore
            }
            proc.destroyForcibly();
        }

        return true;
    }

    private Utils() {
    }
}
