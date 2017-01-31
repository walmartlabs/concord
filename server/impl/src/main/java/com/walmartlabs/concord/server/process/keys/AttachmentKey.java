package com.walmartlabs.concord.server.process.keys;

import java.nio.file.Path;

public final class AttachmentKey extends Key<Path> {

    private static final KeyIndex<AttachmentKey> index = new KeyIndex<>((n, type) -> new AttachmentKey(n));

    public static AttachmentKey register(String name) {
        return index.register(name, Path.class);
    }

    private AttachmentKey(String key) {
        super(key, Path.class);
    }
}
