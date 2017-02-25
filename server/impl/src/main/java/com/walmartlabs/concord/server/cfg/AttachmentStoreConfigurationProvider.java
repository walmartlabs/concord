package com.walmartlabs.concord.server.cfg;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Named
@Singleton
public class AttachmentStoreConfigurationProvider implements Provider<AttachmentStoreConfiguration> {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStoreConfigurationProvider.class);

    public static final String ATTACHMENT_STORE_DIR_KEY = "ATTACHMENT_STORE_DIR";

    @Override
    public AttachmentStoreConfiguration get() {
        try {
            String s = System.getenv(ATTACHMENT_STORE_DIR_KEY);
            Path p = s != null ? Paths.get(s).toAbsolutePath() : Files.createTempDirectory("attachments");
            log.info("get -> using '{}' as attachment storage", p);
            return new AttachmentStoreConfiguration(p);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
