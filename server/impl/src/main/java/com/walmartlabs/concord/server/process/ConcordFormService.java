package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.api.process.FormListEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.project.ConfigurationUtils;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.ResumeHandler;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
@Singleton
public class ConcordFormService {

    private final ProcessAttachmentManager attachmentManager;
    private final FormValidator validator;
    private final ProcessResource processResource;

    @Inject
    public ConcordFormService(ProcessAttachmentManager attachmentManager, ProcessResource processResource) {
        this.attachmentManager = attachmentManager;
        this.processResource = processResource;
        this.validator = new DefaultFormValidator();
    }

    public Form get(String processInstanceId, String formInstanceId) {
        String resource = Constants.JOB_STATE_DIR_NAME + "/" + Constants.JOB_FORMS_DIR_NAME + "/" + formInstanceId;
        Path p = attachmentManager.get(processInstanceId, resource);
        if (p == null) {
            return null;
        }
        return deserialize(p);
    }

    private static Form deserialize(Path p) {
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(p))) {
            return (Form) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error while deserializing a form: " + p, e);
        }
    }

    public List<FormListEntry> list(String processInstanceId) throws ExecutionException {
        String resource = Constants.JOB_STATE_DIR_NAME + "/" + Constants.JOB_FORMS_DIR_NAME + "/";
        try {
            Path p = attachmentManager.get(processInstanceId, resource);
            if (p == null) {
                return Collections.emptyList();
            }

            return Files.list(p)
                    .map(ConcordFormService::deserialize)
                    .map(f -> new FormListEntry(f.getFormInstanceId().toString(), f.getFormDefinition().getName()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ExecutionException("Error while reading list of forms: " + processInstanceId, e);
        }
    }

    public FormSubmitResult submit(String processInstanceId, String formInstanceId, Map<String, Object> data) throws ExecutionException {
        Form form = get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new ExecutionException("Form not found: " + formInstanceId);
        }

        ResumeHandler resumeHandler = (f, args) -> {
            String resource = Constants.JOB_STATE_DIR_NAME + "/" + Constants.JOB_FORMS_DIR_NAME + "/" + formInstanceId;
            try {
                attachmentManager.delete(processInstanceId, resource);
            } catch (IOException e) {
                throw new ExecutionException("Error while removing a form: " + formInstanceId, e);
            }

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put("arguments", args);
            processResource.resume(f.getProcessBusinessKey(), f.getEventName(), m);
        };

        Map<String, Object> merged = merge(form, data);
        return DefaultFormService.submit(resumeHandler, validator, form, merged);
    }

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

        Map<String, Object> a = new HashMap<>(formState != null ? formState : Collections.emptyMap());
        Map<String, Object> b = new HashMap<>(data != null ? data : Collections.emptyMap());

        ConfigurationUtils.merge(a, b);
        return a;
    }
}
