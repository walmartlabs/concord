package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.server.api.process.FormListEntry;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
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
import java.util.*;
import java.util.stream.Collectors;

@Named
@Singleton
public class ConcordFormService {

    private final PayloadManager payloadManager;
    private final FormValidator validator;
    private final Chain resumePipeline;

    @Inject
    public ConcordFormService(
            PayloadManager payloadManager,
            ResumePipeline resumePipeline) {
        this.payloadManager = payloadManager;
        this.resumePipeline = resumePipeline;
        this.validator = new DefaultFormValidator();
    }

    public Form get(String processInstanceId, String formInstanceId) {
        // TODO cache?
        String resource = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" +
                Constants.Files.JOB_STATE_DIR_NAME + "/" +
                Constants.Files.JOB_FORMS_DIR_NAME + "/" +
                formInstanceId;

        Path p = payloadManager.getResource(processInstanceId, resource);
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
        String resource = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" +
                Constants.Files.JOB_STATE_DIR_NAME + "/" +
                Constants.Files.JOB_FORMS_DIR_NAME + "/";

        try {
            Path baseDir = payloadManager.getResource(processInstanceId, resource);
            if (baseDir == null) {
                return Collections.emptyList();
            }

            return Files.list(baseDir)
                    .map(p -> {
                        Form f = deserialize(p);

                        String name = f.getFormDefinition().getName();

                        // TODO constants
                        String s = "forms/" + f.getFormDefinition().getName();
                        Path branding = payloadManager.getResource(processInstanceId, s);

                        return new FormListEntry(f.getFormInstanceId().toString(), name, branding != null);
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new ExecutionException("Error while reading list of forms: " + processInstanceId, e);
        }
    }

    public String nextFormId(String processInstanceId) throws ExecutionException {
        String resource = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" +
                Constants.Files.JOB_STATE_DIR_NAME + "/" +
                Constants.Files.JOB_FORMS_DIR_NAME + "/";

        try {
            Path baseDir = payloadManager.getResource(processInstanceId, resource);
            if (baseDir == null) {
                return null;
            }

            Optional<Path> o = Files.list(baseDir).findFirst();
            return o.map(path -> path.getFileName().toString()).orElse(null);

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
            String resource = Constants.Files.JOB_ATTACHMENTS_DIR_NAME + "/" +
                    Constants.Files.JOB_STATE_DIR_NAME + "/" +
                    Constants.Files.JOB_FORMS_DIR_NAME + "/" +
                    formInstanceId;

            Path p = payloadManager.getResource(processInstanceId, resource);
            try {
                IOUtils.deleteRecursively(p);
            } catch (IOException e) {
                throw new ExecutionException("Error while removing a form: " + formInstanceId, e);
            }

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put("arguments", args);
            resume(f.getProcessBusinessKey(), f.getEventName(), m);
        };

        Map<String, Object> merged = merge(form, data);
        return DefaultFormService.submit(resumeHandler, validator, form, merged);
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

    private void resume(String instanceId, String eventName, Map<String, Object> req) throws ExecutionException {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(instanceId, eventName, req);
        } catch (IOException e) {
            throw new ExecutionException("Error creating a payload for: " + instanceId, e);
        }

        resumePipeline.process(payload);
    }
}
