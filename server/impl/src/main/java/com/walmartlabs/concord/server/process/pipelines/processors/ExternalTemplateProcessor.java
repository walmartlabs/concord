package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.common.DependencyManager;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.cfg.TemplateConfiguration;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.project.TemplateAliasDao;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

@Named
@Singleton
public class ExternalTemplateProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExternalTemplateProcessor.class);

    public static final String REQUEST_DATA_TEMPLATE_FILE_NAME = "_main.js";
    public static final String INPUT_REQUEST_DATA_KEY = "_input";

    private final LogManager logManager;
    private final ScriptEngine scriptEngine;
    private final DependencyManager dependencyManager;
    private final TemplateAliasDao aliasDao;

    @Inject
    public ExternalTemplateProcessor(TemplateConfiguration cfg,
                                     LogManager logManager,
                                     TemplateAliasDao aliasDao) throws IOException {

        this.logManager = logManager;
        this.aliasDao = aliasDao;
        this.scriptEngine = new NashornScriptEngineFactory().getScriptEngine("--no-java");
        this.dependencyManager = new DependencyManager(cfg.getCacheDir());
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String instanceId = payload.getInstanceId();
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

    private URI getUri(String instanceId, String template) throws URISyntaxException {
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
        String instanceId = payload.getInstanceId();
        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);

        // copy template's files to the payload
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(template))) {
            IOUtils.unzip(zip, workspacePath);
        }

        // process _main.json
        Path templateMeta = workspacePath.resolve(REQUEST_DATA_TEMPLATE_FILE_NAME);
        if (Files.exists(templateMeta)) {
            Map in = payload.getHeader(Payload.REQUEST_DATA_MAP);
            Map out = processMeta(instanceId, in, templateMeta);
            payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, out);
        } else {
            log.debug("apply ['{}'] -> no template metadata file found, skipping", workspacePath);
        }

        log.debug("process ['{}', '{}'] -> done", instanceId, template);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processMeta(String instanceId, Map meta, Path templateMeta) throws IOException {
        Object result;
        try (Reader r = new FileReader(templateMeta.toFile())) {
            Bindings b = scriptEngine.createBindings();
            b.put(INPUT_REQUEST_DATA_KEY, meta != null ? meta : Collections.emptyMap());

            result = scriptEngine.eval(r, b);
            if (result == null || !(result instanceof Map)) {
                throw new ProcessException(instanceId, "Invalid template result. Expected a Java Map, got " + result);
            }
        } catch (ScriptException e) {
            logManager.error(instanceId, "Template script execution error", e);
            throw new IOException("Template script execution error", e);
        }
        return (Map<String, Object>) result;
    }
}
