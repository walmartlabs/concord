package com.walmartlabs.concord.server.console;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.FormServerConfiguration;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.FormUtils;
import com.walmartlabs.concord.server.process.FormUtils.ValidationException;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.model.form.FormField.Cardinality;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Named
public class CustomFormServiceImpl implements CustomFormService, Resource {

    private static final Logger log = LoggerFactory.getLogger(CustomFormServiceImpl.class);
    private static final long STATUS_REFRESH_DELAY = 250;

    private final FormServerConfiguration cfg;
    private final ConcordFormService formService;
    private final PayloadManager payloadManager;
    private final ProcessQueueDao queueDao;
    private final ObjectMapper objectMapper;
    private final FormValidatorLocale validatorLocale;

    @Inject
    public CustomFormServiceImpl(FormServerConfiguration cfg, ConcordFormService formService,
                                 PayloadManager payloadManager, ProcessQueueDao queueDao) {

        this.cfg = cfg;
        this.formService = formService;
        this.payloadManager = payloadManager;
        this.queueDao = queueDao;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        this.validatorLocale = new DefaultFormValidatorLocale();
    }

    @Override
    @Validate
    @RequiresAuthentication
    public FormSessionResponse startSession(String processInstanceId, String formInstanceId) {
        // TODO locking
        Form form = assertForm(processInstanceId, formInstanceId);

        Path src = getBranding(form);
        if (src == null) {
            // not branded, redirect to the default wizard
            // TODO const?
            String dst = "/#/process/" + processInstanceId + "/form/" + formInstanceId + "?fullScreen=true&wizard=true";
            return new FormSessionResponse(dst);
        }

        Path dst = cfg.getBaseDir()
                .resolve(processInstanceId)
                .resolve(formInstanceId);

        try {
            if (!Files.exists(dst)) {
                Files.createDirectories(dst);
            }

            // copy original branding files into the target directory
            IOUtils.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            // create JS file containing the form's data
            FormData d = prepareData(form, null, null);
            writeData(dst, d);
        } catch (IOException e) {
            log.warn("startSession ['{}', '{}'] -> error while preparing a custom form: {}",
                    processInstanceId, formInstanceId, e);
            throw new WebApplicationException("Error while preparing a custom form", e);
        }

        return new FormSessionResponse(formPath(processInstanceId, formInstanceId));
    }

    @Override
    @Validate
    @RequiresAuthentication
    public Response continueSession(UriInfo uriInfo, String processInstanceId, String formInstanceId, MultivaluedMap<String, String> data) {
        // TODO locking
        Form form = assertForm(processInstanceId, formInstanceId);

        // TODO constants
        Map<String, Object> opts = form.getOptions();
        boolean yield = opts != null && (boolean) opts.getOrDefault("yield", false);

        Path dst = cfg.getBaseDir()
                .resolve(processInstanceId)
                .resolve(formInstanceId);

        try {
            Map<String, Object> m = new HashMap<>();
            try {
                m = FormUtils.convert(validatorLocale, form, FormUtils.convert(data));

                FormSubmitResult r = formService.submit(processInstanceId, formInstanceId, m);
                if (r.isValid()) {
                    if (yield) {
                        // this was the last "interactive" form. The process will continue in "background"
                        // and users should get a success page.
                        writeData(dst, success());
                    } else {
                        while (true) {
                            ProcessEntry entry = queueDao.get(processInstanceId);
                            ProcessStatus s = entry.getStatus();

                            if (s == ProcessStatus.SUSPENDED) {
                                String nextFormId = formService.nextFormId(processInstanceId);
                                if (nextFormId == null) {
                                    writeData(dst, success());
                                    break;
                                } else {
                                    FormSessionResponse nextSession = startSession(processInstanceId, nextFormId);
                                    return redirectTo(nextSession.getUri());
                                }
                            } else if (s == ProcessStatus.FAILED) {
                                writeData(dst, processFailed());
                                break;
                            } else if (s == ProcessStatus.FINISHED) {
                                writeData(dst, success());
                                break;
                            }

                            try {
                                // TODO exp back off?
                                Thread.sleep(STATUS_REFRESH_DELAY);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                } else {
                    writeData(dst, prepareData(form, m, r.getErrors()));
                }
            } catch (ValidationException e) {
                ValidationError err = new ValidationError(e.getField().getName(), e.getMessage());
                FormData d = prepareData(form, m, Collections.singletonList(err));
                writeData(dst, d);
            }
        } catch (IOException | ExecutionException e) {
            throw new WebApplicationException("Error while submitting a form", e);
        }

        return redirectToForm(uriInfo, processInstanceId, formInstanceId);
    }

    private Form assertForm(String processInstanceId, String formInstanceId) {
        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            log.warn("assertForm ['{}', '{}'] -> not found", processInstanceId, formInstanceId);
            throw new WebApplicationException("Form not found", Status.NOT_FOUND);
        }
        return form;
    }

    private Path getBranding(Form f) {
        // TODO constants
        String resource = "forms/" + f.getFormDefinition().getName();
        Path path = payloadManager.getResource(f.getProcessBusinessKey(), resource);
        if (path == null) {
            return null;
        }

        if (!Files.isDirectory(path)) {
            log.warn("getBranding ['{}'] -> expected a directory: {}", f.getFormInstanceId(), resource);
        }

        return path;
    }

    @SuppressWarnings("unchecked")
    private FormData prepareData(Form form, Map<String, Object> overrides, List<ValidationError> errors) {
        // TODO constants
        String processInstanceId = form.getProcessBusinessKey();
        String formInstanceId = form.getFormInstanceId().toString();

        String submitUrl = "/api/service/custom_form/" + processInstanceId + "/" + formInstanceId + "/continue";
        Map<String, FormDataDefinition> _definitions = new HashMap<>();
        Map<String, Object> _values = new HashMap<>();
        Map<String, String> _errors = new HashMap<>();

        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> env = form.getEnv();
        Map<String, Object> data = env != null ? (Map<String, Object>) env.get(fd.getName()) : Collections.emptyMap();
        if (data == null) {
            data = Collections.emptyMap();
        }

        for (FormField f : fd.getFields()) {
            _definitions.put(f.getName(), new FormDataDefinition(f.getType(), f.getCardinality(), f.getAllowedValue()));

            Object v = overrides != null ? overrides.get(f.getName()) : null;
            if (v == null) {
                v = data.get(f.getName());
            }

            if (v == null) {
                continue;
            }

            _values.put(f.getName(), v);
        }

        if (errors != null) {
            for (ValidationError e : errors) {
                _errors.put(e.getFieldName(), e.getError());
            }
        }

        return new FormData(submitUrl, _definitions, _values, _errors);
    }

    private static Response redirectToForm(UriInfo uriInfo, String processInstanceId, String formInstanceId) {
        UriBuilder b = UriBuilder.fromUri(uriInfo.getBaseUri())
                .path(formPath(processInstanceId, formInstanceId));
        return redirectTo(b.build().toString());
    }

    private static Response redirectTo(String path) {
        return Response.status(Status.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, path)
                .build();
    }

    private static String formPath(String processInstanceId, String formInstanceId) {
        return String.format(FORMS_PATH_PATTERN, processInstanceId, formInstanceId);
    }

    private static Map<String, Object> success() {
        return Collections.singletonMap("success", true);
    }

    private static Map<String, Object> processFailed() {
        return Collections.singletonMap("processFailed", true);
    }

    private void writeData(Path baseDir, Object data) throws IOException {
        Path dst = baseDir.resolve("data.js");
        String s = String.format(DATA_FILE_TEMPLATE, objectMapper.writeValueAsString(data));
        Files.write(dst, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    @JsonInclude(Include.NON_EMPTY)
    private static class FormData implements Serializable {

        private final String submitUrl;
        private final Map<String, FormDataDefinition> definitions;
        private final Map<String, Object> values;
        private final Map<String, String> errors;

        public FormData(String submitUrl, Map<String, FormDataDefinition> definitions, Map<String, Object> values, Map<String, String> errors) {
            this.submitUrl = submitUrl;
            this.definitions = definitions;
            this.values = values;
            this.errors = errors;
        }

        public String getSubmitUrl() {
            return submitUrl;
        }

        public Map<String, FormDataDefinition> getDefinitions() {
            return definitions;
        }

        public Map<String, Object> getValues() {
            return values;
        }

        public Map<String, String> getErrors() {
            return errors;
        }
    }

    @JsonInclude(Include.NON_NULL)
    private static class FormDataDefinition implements Serializable {

        private final String type;
        private final Cardinality cardinality;
        private final Object allow;

        private FormDataDefinition(String type, Cardinality cardinality, Object allow) {
            this.type = type;
            this.cardinality = cardinality;
            this.allow = allow;
        }

        public String getType() {
            return type;
        }

        public Cardinality getCardinality() {
            return cardinality;
        }

        public Object getAllow() {
            return allow;
        }
    }
}
