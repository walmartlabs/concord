package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.FormListEntry;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormService;
import io.takari.bpm.form.DefaultFormService.ResumeHandler;
import io.takari.bpm.form.DefaultFormValidator;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
@Singleton
public class ConcordFormService {

    public static final String FORMS_RESOURCES_PATH = "forms";

    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final FormValidator validator;
    private final Chain resumePipeline;

    @Inject
    public ConcordFormService(
            PayloadManager payloadManager,
            ProcessStateManager stateManager,
            ResumePipeline resumePipeline) {

        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.resumePipeline = resumePipeline;

        this.validator = new DefaultFormValidator();
    }

    public Form get(UUID processInstanceId, String formInstanceId) {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.JOB_FORMS_DIR_NAME,
                formInstanceId);

        Optional<Form> o = stateManager.get(processInstanceId, resource, ConcordFormService::deserialize);
        return o.orElse(null);
    }

    private static Optional<Form> deserialize(InputStream data) {
        try (ObjectInputStream in = new ObjectInputStream(data)) {
            return Optional.ofNullable((Form) in.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error while deserializing a form", e);
        }
    }

    public List<FormListEntry> list(UUID processInstanceId) throws ExecutionException {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.JOB_FORMS_DIR_NAME);

        List<Form> forms = stateManager.forEach(processInstanceId, resource, ConcordFormService::deserialize);
        return forms.stream().map(f -> {
            String name = f.getFormDefinition().getName();

            String s = FORMS_RESOURCES_PATH + "/" + f.getFormDefinition().getName();
            boolean branding = stateManager.exists(processInstanceId, s);
            boolean yield = getBoolean(f.getOptions(), "yield", false);

            return new FormListEntry(f.getFormInstanceId().toString(), name, branding, yield);
        }).collect(Collectors.toList());
    }

    public String nextFormId(UUID processInstanceId) throws ExecutionException {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.JOB_FORMS_DIR_NAME);

        Function<String, Optional<String>> getId = s -> {
            int i = s.lastIndexOf("/");
            if (i < 0 || i + 1 >= s.length()) {
                return Optional.empty();
            }
            return Optional.of(s.substring(i + 1));
        };

        // TODO this probably should be replaced with ProcessStateManager#findFirst
        Optional<String> o = stateManager.findPath(processInstanceId, resource,
                files -> files.findFirst().flatMap(getId));

        return o.orElse(null);
    }

    public FormSubmitResult submit(UUID processInstanceId, String formInstanceId, Map<String, Object> data) {
        Form form = get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new ProcessException(processInstanceId, "Form not found: " + formInstanceId);
        }

        ResumeHandler resumeHandler = (f, args) -> {
            String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                    InternalConstants.Files.JOB_STATE_DIR_NAME,
                    InternalConstants.Files.JOB_FORMS_DIR_NAME,
                    formInstanceId);

            stateManager.delete(processInstanceId, resource);

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put(InternalConstants.Request.ARGUMENTS_KEY, args);
            resume(UUID.fromString(f.getProcessBusinessKey()), f.getEventName(), m);
        };

        Map<String, Object> merged = merge(form, data);
        try {
            return toResult(processInstanceId, form, DefaultFormService.submit(resumeHandler, validator, form, merged));
        } catch (ExecutionException e) {
            throw new ProcessException(processInstanceId, "Form submit error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public FormSubmitResult submitNext(UUID processInstanceId, Map<String, Object> data) {
        List<FormListEntry> forms;
        try {
            forms = list(processInstanceId);
        } catch (ExecutionException e) {
            throw new ProcessException(processInstanceId, "Process execution error", e);
        }

        if (forms == null || forms.isEmpty()) {
            throw new ProcessException(processInstanceId, "Invalid process state: no forms found");
        }

        FormSubmitResult lastResult = null;
        for (FormListEntry f : forms) {
            Map<String, Object> args = (Map<String, Object>) data.get(f.getName());

            lastResult = submit(processInstanceId, f.getFormInstanceId(), args);
            if (!lastResult.isValid()) {
                return lastResult;
            }
        }
        return lastResult;
    }

    private static FormSubmitResult toResult(UUID processInstanceId, Form f, io.takari.bpm.form.FormSubmitResult r) {
        return new FormSubmitResult(processInstanceId, f.getFormDefinition().getName(), r.getErrors());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Form form, Map<String, Object> data) {
        String formName = form.getFormDefinition().getName();

        Map<String, Object> env = form.getEnv();
        if (env == null) {
            env = Collections.emptyMap();
        }

        Map<String, Object> formState = (Map<String, Object>) env.get(formName);
        if (formState == null) {
            formState = Collections.emptyMap();
        }

        Map<String, Object> a = new HashMap<>(formState);
        Map<String, Object> b = new HashMap<>(data != null ? data : Collections.emptyMap());

        ConfigurationUtils.merge(a, b);
        return a;
    }

    private void resume(UUID instanceId, String eventName, Map<String, Object> req) throws ExecutionException {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(instanceId, eventName, req);
        } catch (IOException e) {
            throw new ExecutionException("Error while creating a payload for: " + instanceId, e);
        }

        resumePipeline.process(payload);
    }

    private static boolean getBoolean(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null) {
            return defaultValue;
        }

        Object v = options.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (!(v instanceof Boolean)) {
            throw new IllegalArgumentException("Expected a boolean value: " + key);
        }

        return (Boolean) v;
    }

    public static final class FormSubmitResult implements Serializable {

        private final UUID processInstanceId;
        private final String formName;
        private final List<ValidationError> errors;

        public FormSubmitResult(UUID processInstanceId, String formName, List<ValidationError> errors) {
            this.processInstanceId = processInstanceId;
            this.formName = formName;
            this.errors = errors;
        }

        public UUID getProcessInstanceId() {
            return processInstanceId;
        }

        public String getFormName() {
            return formName;
        }

        public List<ValidationError> getErrors() {
            return errors;
        }

        public boolean isValid() {
            return errors == null || errors.isEmpty();
        }
    }
}
