package com.walmartlabs.concord.server.template;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.pipelines.processors.PayloadProcessor;
import jdk.nashorn.api.scripting.NashornScriptEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.ws.rs.core.Response.Status;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

@Named
public class TemplateProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    private final TemplateDao templateDao;
    private final TemplateResolver templateResolver;
    private final ScriptEngine scriptEngine;

    @Inject
    public TemplateProcessor(TemplateDao templateDao, TemplateResolver templateResolver) {
        this.templateDao = templateDao;
        this.templateResolver = templateResolver;
        this.scriptEngine = new NashornScriptEngineFactory().getScriptEngine("--no-java");
    }

    @Override
    public Payload process(Payload payload) {
        String projectName = payload.getHeader(Payload.PROJECT_NAME);
        if (projectName != null) {
            return processProject(payload, projectName);
        }

        Map<String, Object> req = payload.getHeader(Payload.REQUEST_DATA_MAP);
        if (req == null) {
            return payload;
        }

        String templateName = (String) req.get(Constants.TEMPLATE_KEY);
        if (templateName == null) {
            return payload;
        }

        try {
            Path templatePath = templateResolver.get(templateName);
            return process(payload, templatePath);
        } catch (IOException e) {
            log.error("process ['{}'] -> error", payload.getInstanceId(), e);
            throw new ProcessException("Error while processing a template", e);
        }
    }

    private Payload processProject(Payload payload, String projectName) {
        Collection<String> templateNames = templateDao.getProjectTemplates(projectName);
        if (templateNames.isEmpty()) {
            return payload;
        }
        if (templateNames.size() > 1) {
            throw new ProcessException("Multiple project templates are not yet supported", Status.BAD_REQUEST);
        }

        try {
            String templateName = templateNames.iterator().next();
            Path templatePath = templateResolver.get(templateName);
            return process(payload, templatePath);
        } catch (IOException e) {
            log.error("process ['{}'] -> error", payload.getInstanceId(), e);
            throw new ProcessException("Error while processing a template", e);
        }
    }

    private Payload process(Payload payload, Path templatePath) throws IOException {
        if (templatePath == null) {
            throw new ProcessException("Template not found: " + templatePath);
        }

        Path workspacePath = payload.getHeader(Payload.WORKSPACE_DIR);

        // copy template's files to the payload
        IOUtils.copy(templatePath, workspacePath);

        // process _main.json
        Path templateMeta = templatePath.resolve(TemplateConstants.REQUEST_DATA_TEMPLATE_FILE_NAME);
        if (Files.exists(templateMeta)) {
            Map in = payload.getHeader(Payload.REQUEST_DATA_MAP);
            Map out = processMeta(in, templateMeta);
            payload = payload.mergeValues(Payload.REQUEST_DATA_MAP, out);
        } else {
            log.debug("apply ['{}'] -> no template metadata file found, skipping", workspacePath);
        }

        log.debug("process ['{}', '{}'] -> done", payload.getInstanceId(), templatePath);
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
                throw new ProcessException("Invalid template result. Expected a Java Map, got " + result);
            }
        } catch (ScriptException e) {
            throw new IOException("Template script execution error", e);
        }
        return (Map<String, Object>) result;
    }
}
