package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import javax.inject.Named;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

@Named
public class ResourceTask implements Task {

    @Override
    public String getKey() {
        return "resource";
    }

    public String asString(String path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new BufferedInputStream(new FileInputStream(path))) {
            byte[] ab = Files.readAllBytes(Paths.get(path));
            return new String(ab);
        }
    }
}
