package com.walmartlabs.concord.server.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessExecutorException;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.zip.ZipInputStream;

@Named
@Singleton
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);

    private final TemplateDao templateDao;
    private final ObjectMapper objectMapper;
    private final ScriptEngine scriptEngine;

    @Inject
    public TemplateEngine(TemplateDao templateDao) {
        this.templateDao = templateDao;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.scriptEngine = new NashornScriptEngineFactory().getScriptEngine("--no-java");
    }

    public void process(Payload payload) throws ProcessExecutorException {
        try {
            apply(payload.getData(), payload.getProjectId());
        } catch (TemplateException e) {
            throw new ProcessExecutorException("Error while applying a template to the payload", e);
        }
    }

    private void apply(Path payload, String projectId) throws TemplateException {
        Collection<String> templateIds = templateDao.getProjectTemplateIds(projectId);
        if (templateIds.isEmpty()) {
            log.info("apply ['{}'] -> no templates found, skipping...", projectId);
            return;
        }
        if (templateIds.size() > 1) {
            throw new TemplateException("Only one project template is supported for the moment");
        }

        try (InputStream in = templateDao.get(templateIds.iterator().next())) {
            apply(payload, in);
        } catch (IOException e) {
            throw new TemplateException("Error while applying a template", e);
        }
    }

    private void apply(Path payloadPath, InputStream template) throws TemplateException {
        Path templatePath;
        try {
            templatePath = Files.createTempDirectory("template");
        } catch (IOException e) {
            throw new TemplateException("Can't create a temporary directory", e);
        }

        unpack(template, templatePath);

        // process _main.json

        Path payloadMeta = payloadPath.resolve(Constants.METADATA_FILE_NAME);
        Path templateMeta = templatePath.resolve(TemplateConstants.METADATA_TEMPLATE_FILENAME);
        if (Files.exists(templateMeta)) {
            Map<String, Object> dstMeta = processMeta(payloadMeta, templateMeta);
            storeMeta(payloadPath, dstMeta);
            log.debug("apply ['{}'] -> saved a new metadata file: {}", payloadPath, payloadMeta);
        } else {
            log.debug("apply ['{}'] -> no template metadata file found, skipping", payloadPath);
        }

        // copy libraries from the template to the payload

        copyResources(templatePath, Constants.LIBRARIES_DIR_NAME, payloadPath);

        // copy process definitions from the template to the payload

        copyResources(templatePath, Constants.DEFINITIONS_DIR_NAME, payloadPath);
    }

    private Map<String, Object> processMeta(Path payloadMeta, Path templateMeta) throws TemplateException {
        Map<String, Object> meta;
        try {
            meta = objectMapper.readValue(payloadMeta.toFile(), Map.class);
        } catch (IOException e) {
            throw new TemplateException("Error while reading payload's metadata", e);
        }

        Object result;
        try (Reader r = new FileReader(templateMeta.toFile())) {
            Bindings b = scriptEngine.createBindings();
            b.put(TemplateConstants.INPUT_METADATA_KEY, meta);

            result = scriptEngine.eval(r, b);
            if (result == null || !(result instanceof Map)) {
                throw new TemplateException("Invalid template result. Expected a Java Map, got %s", result);
            }
        } catch (ScriptException | IOException e) {
            throw new TemplateException("Error while processing a template", e);
        }
        return (Map<String, Object>) result;
    }

    private void storeMeta(Path payloadPath, Map<String, Object> meta) throws TemplateException {
        Path p = payloadPath.resolve(Constants.METADATA_FILE_NAME);
        try {
            objectMapper.writeValue(p.toFile(), meta);
        } catch (IOException e) {
            throw new TemplateException("Error while writing a new metadata", e);
        }
    }

    private static void unpack(InputStream in, Path dir) throws TemplateException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(in))) {
            IOUtils.unzip(zip, dir);
        } catch (IOException e) {
            throw new TemplateException("Error while unpacking an archive", e);
        }
    }

    private static void copyResources(Path templatePath, String dir, Path payloadPath) throws TemplateException {
        Path src = templatePath.resolve(dir);
        if (Files.exists(src)) {
            try {
                Path dst = payloadPath.resolve(dir);
                IOUtils.copy(src, dst);
                log.debug("copyResources ['{}', '{}', '{}'] -> done", templatePath, dir, payloadPath);
            } catch (IOException e) {
                throw new TemplateException("Error while copying template's resources", e);
            }
        } else {
            log.debug("copyResources ['{}', '{}', '{}'] -> not found, skipping", templatePath, dir, payloadPath);
        }
    }
}
