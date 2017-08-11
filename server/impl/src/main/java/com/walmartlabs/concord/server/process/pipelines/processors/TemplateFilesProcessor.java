package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.DependencyManager;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.cfg.TemplateConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.project.TemplateAliasDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Named
@Singleton
/**
 * Extracts template files into the workspace.
 */
public class TemplateFilesProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateFilesProcessor.class);

    private final LogManager logManager;
    private final DependencyManager dependencyManager;
    private final TemplateAliasDao aliasDao;

    @Inject
    public TemplateFilesProcessor(TemplateConfiguration cfg,
                                  LogManager logManager,
                                  TemplateAliasDao aliasDao) throws IOException {

        this.logManager = logManager;
        this.aliasDao = aliasDao;
        this.dependencyManager = new DependencyManager(cfg.getCacheDir());
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);

        String s = (String) req.get(Constants.Request.TEMPLATE_KEY);
        if (s == null) {
            return chain.process(payload);
        }

        try {
            URI uri = getUri(instanceId, s);
            Path template = dependencyManager.resolve(uri);
            payload = process(payload, template);

            return chain.process(payload);
        } catch (URISyntaxException | IOException e) {
            logManager.error(instanceId, "Template error: " + s, e);
            throw new ProcessException(instanceId, "Error while processing a template: " + s, e);
        }
    }

    private URI getUri(UUID instanceId, String template) throws URISyntaxException {
        try {
            return new URL(template).toURI();
        } catch (MalformedURLException e) {
            // doesn't look like a URI, let's try find an alias

            Optional<String> o = aliasDao.get(template);
            if (!o.isPresent()) {
                throw new ProcessException(instanceId, "Invalid template URL or alias: " + template);
            }

            return new URI(o.get());
        }
    }

    private Payload process(Payload payload, Path template) throws IOException {
        UUID instanceId = payload.getInstanceId();
        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);

        // copy template's files to the payload, skipping the existing files
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(template))) {
            IOUtils.unzip(zip, workspacePath, true);
        }

        log.debug("process ['{}', '{}'] -> done", instanceId, template);
        return payload;
    }
}
