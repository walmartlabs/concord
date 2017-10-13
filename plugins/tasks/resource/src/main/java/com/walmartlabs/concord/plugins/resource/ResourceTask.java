package com.walmartlabs.concord.plugins.resource;

import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Named("resource")
public class ResourceTask implements Task {

    public String asString(String path) throws IOException {
        byte[] ab = Files.readAllBytes(Paths.get(path));
        return new String(ab);
    }
}
