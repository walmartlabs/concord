package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipInputStream;

@Named
public class TemplateProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    private final TemplateDao templateDao;
    private final ScriptEngine scriptEngine;

    @Inject
    public TemplateProcessor(TemplateDao templateDao) {
        this.templateDao = templateDao;
        this.scriptEngine = new NashornScriptEngineFactory().getScriptEngine("--no-java");
    }

    @Override
    public Payload process(Payload payload) {
        String projectId = payload.getHeader(Payload.PROJECT_ID);
        if (projectId == null) {
            return payload;
        }

        Collection<String> templateIds = templateDao.getProjectTemplateIds(projectId);
        if (templateIds.isEmpty()) {
            return payload;
        }
        if (templateIds.size() > 1) {
            throw new WebApplicationException("Multiple project templates are not yet supported", Status.BAD_REQUEST);
        }

        try (InputStream in = templateDao.get(templateIds.iterator().next())) {
            return process(payload, in);
        } catch (IOException e) {
            throw new WebApplicationException("Error while processing a template", e);
        }
    }

    private Payload process(Payload payload, InputStream template) throws IOException {
        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);
        Path templatePath = Files.createTempDirectory("template");

        unpack(template, templatePath);

        // process _main.json
        Path templateMeta = templatePath.resolve(TemplateConstants.REQUEST_DATA_TEMPLATE_FILE_NAME);
        if (Files.exists(templateMeta)) {
            Map in = payload.getHeader(Payload.REQUEST_DATA_MAP);
            Map out = processMeta(in, templateMeta);
            payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, out);
        } else {
            log.debug("apply ['{}'] -> no template metadata file found, skipping", workspacePath);
        }

        // copy libraries from the template to the payload
        copyResources(templatePath, Constants.LIBRARIES_DIR_NAME, workspacePath);

        // copy process definitions from the template to the payload
        copyResources(templatePath, Constants.DEFINITIONS_DIR_NAME, workspacePath);

        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> processMeta(Map meta, Path templateMeta) throws IOException {
        Object result;
        try (Reader r = new FileReader(templateMeta.toFile())) {
            Bindings b = scriptEngine.createBindings();
            b.put(TemplateConstants.INPUT_REQUEST_DATA_KEY, meta != null ? meta : Collections.emptyMap());

            result = scriptEngine.eval(r, b);
            if (result == null || !(result instanceof Map)) {
                throw new WebApplicationException("Invalid template result. Expected a Java Map, got " + result);
            }
        } catch (ScriptException e) {
            throw new IOException("Template script execution error", e);
        }
        return (Map<String, Object>) result;
    }

    private static void unpack(InputStream in, Path dir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(in))) {
            IOUtils.unzip(zip, dir);
        }
    }

    private static void copyResources(Path templatePath, String dir, Path payloadPath) throws IOException {
        Path src = templatePath.resolve(dir);
        if (Files.exists(src)) {
            Path dst = payloadPath.resolve(dir);
            IOUtils.copy(src, dst);
            log.debug("copyResources ['{}', '{}', '{}'] -> done", templatePath, dir, payloadPath);
        } else {
            log.debug("copyResources ['{}', '{}', '{}'] -> not found, skipping", templatePath, dir, payloadPath);
        }
    }
}
