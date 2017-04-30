package com.walmartlabs.concord.server.console;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.history.ProcessHistoryEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.FormServerConfiguration;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.process.ConcordFormService;
import com.walmartlabs.concord.server.process.PayloadManager;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.DefaultFormFields;
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
import java.util.*;

@Named
public class CustomFormServiceImpl implements CustomFormService, Resource {

    private static final Logger log = LoggerFactory.getLogger(CustomFormServiceImpl.class);

    private final FormServerConfiguration cfg;
    private final ConcordFormService formService;
    private final PayloadManager payloadManager;
    private final ProcessHistoryDao historyDao;
    private final ObjectMapper objectMapper;
    private final FormValidatorLocale validatorLocale;

    @Inject
    public CustomFormServiceImpl(FormServerConfiguration cfg, ConcordFormService formService, PayloadManager payloadManager, ProcessHistoryDao historyDao) {
        this.cfg = cfg;
        this.formService = formService;
        this.payloadManager = payloadManager;
        this.historyDao = historyDao;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        this.validatorLocale = new DefaultFormValidatorLocale();
    }

    @Override
    @Validate
    @RequiresAuthentication
    public FormSessionResponse startSession(String processInstanceId, String formInstanceId) {
        // TODO locking
        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new WebApplicationException("Form not found: " + formInstanceId, Status.BAD_REQUEST);
        }

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
            FormData d = prepareData(processInstanceId, formInstanceId, null, null);
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
        boolean lastForm = opts != null ? (boolean) opts.getOrDefault("lastForm", false) : false;

        Path dst = cfg.getBaseDir()
                .resolve(processInstanceId)
                .resolve(formInstanceId);

        try {
            Map<String, Object> m = new HashMap<>();
            try {
                m = convert(form, convert(data));

                FormSubmitResult r = formService.submit(processInstanceId, formInstanceId, m);
                if (r.isValid()) {
                    if (lastForm) {
                        writeData(dst, new FormData(true));
                    } else {
                        while (true) {
                            ProcessHistoryEntry entry = historyDao.get(processInstanceId);
                            ProcessStatus s = entry.getStatus();

                            if (s == ProcessStatus.SUSPENDED) {
                                String nextFormId = formService.nextFormId(processInstanceId);
                                if (nextFormId == null) {
                                    writeData(dst, new FormData(true));
                                    break;
                                } else {
                                    FormSessionResponse nextSession = startSession(processInstanceId, nextFormId);
                                    return redirectTo(nextSession.getUri());
                                }
                            } else if (s == ProcessStatus.FAILED) {
                                ValidationError err = new ValidationError("ERROR", "Process has failed");
                                writeData(dst, prepareData(processInstanceId, formInstanceId, m, Collections.singletonList(err)));
                                break;
                            } else if (s == ProcessStatus.FINISHED) {
                                writeData(dst, new FormData(true));
                                break;
                            }

                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                } else {
                    writeData(dst, prepareData(processInstanceId, formInstanceId, m, r.getErrors()));
                }
            } catch (ValidationException e) {
                ValidationError err = new ValidationError(e.field.getName(), e.message);
                FormData d = prepareData(processInstanceId, formInstanceId, m, Collections.singletonList(err));
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

    private FormData prepareData(String processInstanceId, String formInstanceId, Map<String, Object> overrides, List<ValidationError> errors) {
        // TODO constants
        String submitUrl = "/api/service/custom_form/" + processInstanceId + "/" + formInstanceId + "/continue";
        Map<String, FormDataDefinition> _definitions = new HashMap<>();
        Map<String, Object> _values = new HashMap<>();
        Map<String, String> _errors = new HashMap<>();

        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new WebApplicationException("Form not found", Status.NOT_FOUND);
        }

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

    private void writeData(Path baseDir, FormData data) throws IOException {
        Path dst = baseDir.resolve("data.js");
        String s = String.format(DATA_FILE_TEMPLATE, objectMapper.writeValueAsString(data));
        Files.write(dst, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static FormField findField(FormDefinition fd, String fieldName) {
        for (FormField f : fd.getFields()) {
            if (fieldName.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    private static Map<String, Object> convert(MultivaluedMap<String, String> data) {
        Map<String, Object> m = new HashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (v == null) {
                    return;
                }

                int size = v.size();
                if (size == 0) {
                    return;
                } else if (size == 1) {
                    m.put(k, v.get(0));
                } else {
                    m.put(k, v);
                }
            });
        }
        return m;
    }

    // TODO this probably should be a part of the bpm engine's FormService
    private Map<String, Object> convert(Form form, Map<String, Object> m) throws ValidationException {
        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> m2 = new HashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            String k = e.getKey();

            FormField f = findField(fd, k);
            if (f == null) {
                continue;
            }

            Object v = convert(fd.getName(), f, null, e.getValue());
            if (v == null) {
                continue;
            }

            m2.put(k, v);
        }
        return m2;
    }

    private Object convert(String formName, FormField f, Integer idx, Object v) throws ValidationException {
        if (v instanceof String) {
            String s = (String) v;
            if (s.isEmpty()) {
                return null;
            }

            switch (f.getType()) {
                case DefaultFormFields.IntegerField.TYPE: {
                    try {
                        return Long.parseLong(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, validatorLocale.expectedInteger(formName, f.getName(), idx, s));
                    }
                }
                case DefaultFormFields.DecimalField.TYPE: {
                    try {
                        return Double.parseDouble(s);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(f, s, validatorLocale.expectedDecimal(formName, f.getName(), idx, s));
                    }
                }
            }
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                return null;
            }

            List<Object> ll = new ArrayList<>(l.size());
            int i = 0;
            for (Object o : l) {
                ll.add(convert(formName, f, i, o));
                i++;
            }
            return ll;
        }

        return v;
    }

    private static class ValidationException extends Exception {

        private final FormField field;
        private final String input;
        private final String message;

        private ValidationException(FormField field, String input, String message) {
            this.field = field;
            this.input = input;
            this.message = message;
        }
    }

    @JsonInclude(Include.NON_EMPTY)
    private static class FormData implements Serializable {

        private final String submitUrl;
        private final boolean success;
        private final Map<String, FormDataDefinition> definitions;
        private final Map<String, Object> values;
        private final Map<String, String> errors;

        public FormData(String submitUrl, Map<String, FormDataDefinition> definitions, Map<String, Object> values, Map<String, String> errors) {
            this(submitUrl, false, definitions, values, errors);
        }

        private FormData(boolean success) {
            this(null, success, null, null, null);
        }

        public FormData(String submitUrl, boolean success, Map<String, FormDataDefinition> definitions, Map<String, Object> values, Map<String, String> errors) {
            this.submitUrl = submitUrl;
            this.success = success;
            this.definitions = definitions;
            this.values = values;
            this.errors = errors;
        }

        public String getSubmitUrl() {
            return submitUrl;
        }

        public boolean isSuccess() {
            return success;
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
