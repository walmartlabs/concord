package com.walmartlabs.concord.agent;

import groovy.lang.DelegatingMetaClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileMetaClass extends DelegatingMetaClass {

    private static final Logger log = LoggerFactory.getLogger(FileMetaClass.class);

    private final File baseDir;

    public FileMetaClass(File baseDir) {
        super(File.class);
        this.baseDir = baseDir;
    }

    @Override
    public Object invokeConstructor(Object[] arguments) {
        if (arguments == null || arguments.length != 1 || !(arguments[0] instanceof String)) {
            return super.invokeConstructor(arguments);
        }

        String path = arguments[0].toString();

        if (path.startsWith(File.separator)) {
            return super.invokeConstructor(arguments);
        }

        String newPath = new File(baseDir, path).getAbsolutePath();
        log.info("Replaced '{}' with '{}'", path, newPath);
        return super.invokeConstructor(new Object[]{newPath});
    }
}
