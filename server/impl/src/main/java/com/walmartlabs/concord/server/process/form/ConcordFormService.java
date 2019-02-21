package com.walmartlabs.concord.server.process.form;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.common.form.ConcordFormValidator;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.ldap.LdapManager;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.*;
import io.takari.bpm.form.DefaultFormService.ResumeHandler;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.model.form.FormDefinition;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.form.ConcordFormFields.FieldOptions.READ_ONLY;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

@Named
@Singleton
public class ConcordFormService {

    public static final String FORMS_RESOURCES_PATH = "forms";
    public static final String INTERNAL_RUN_AS_KEY = "_runAs";

    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final FormAccessManager formAccessManager;
    private final LdapManager ldapManager;
    private final Chain resumePipeline;

    @Inject
    public ConcordFormService(
            PayloadManager payloadManager,
            ProcessStateManager stateManager,
            FormAccessManager formAccessManager,
            LdapManager ldapManager,
            ResumePipeline resumePipeline) {

        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.formAccessManager = formAccessManager;
        this.ldapManager = ldapManager;
        this.resumePipeline = resumePipeline;
    }

    public Form get(PartialProcessKey processKey, String formName) {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return get(new ProcessKey(processKey, createdAt), formName);
    }

    public Form get(ProcessKey processKey, String formName) {
        return formAccessManager.assertFormAccess(processKey, formName);
    }

    private static Optional<Form> deserialize(InputStream data) {
        try (ObjectInputStream in = new ObjectInputStream(data)) {
            return Optional.ofNullable((Form) in.readObject());
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Error while deserializing a form", e);
        }
    }

    public List<FormListEntry> list(PartialProcessKey processKey) {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return list(new ProcessKey(processKey, createdAt));
    }

    @SuppressWarnings("unchecked")
    public List<FormListEntry> list(ProcessKey processKey) {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.JOB_FORMS_DIR_NAME);

        List<Form> forms = stateManager.forEach(processKey, resource, ConcordFormService::deserialize);
        return forms.stream().map(f -> {
            String name = f.getFormDefinition().getName();

            String s = FORMS_RESOURCES_PATH + "/" + f.getFormDefinition().getName();
            boolean branding = stateManager.exists(processKey, s);

            Map<String, Object> opts = f.getOptions();
            boolean yield = getBoolean(opts, "yield", false);
            Map<String, Object> runAs = getMap(opts, InternalConstants.Forms.RUN_AS_KEY, null);

            return new FormListEntry(name, branding, yield, runAs);
        }).collect(Collectors.toList());
    }

    public String nextFormId(ProcessKey processKey) {
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
        Optional<String> o = stateManager.findPath(processKey, resource,
                files -> files.findFirst().flatMap(getId));

        return o.orElse(null);
    }

    public FormSubmitResult submit(PartialProcessKey processKey, String formName, Map<String, Object> data) {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return submit(new ProcessKey(processKey, createdAt), formName, data);
    }

    public FormSubmitResult submit(ProcessKey processKey, String formName, Map<String, Object> data) {
        Form form = get(processKey, formName);
        if (form == null) {
            throw new ProcessException(processKey, "Form not found: " + formName);
        }

        ResumeHandler resumeHandler = (f, args) -> {
            String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                    InternalConstants.Files.JOB_STATE_DIR_NAME,
                    InternalConstants.Files.JOB_FORMS_DIR_NAME,
                    formName);

            stateManager.deleteFile(processKey, resource);

            @SuppressWarnings("unchecked")
            Map<String, Object> clearedData = (Map<String, Object>) args.get(f.getFormDefinition().getName());

            args.put(f.getFormDefinition().getName(), removeReadonly(f, clearedData));

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put(InternalConstants.Request.ARGUMENTS_KEY, args);
            if (data != null) {
                m.put(InternalConstants.Files.FORM_FILES, data.remove(InternalConstants.Files.FORM_FILES));
            }

            Map<String, Object> opts = f.getOptions();
            Object runAs = opts != null ? opts.get(InternalConstants.Forms.RUN_AS_KEY) : null;
            if (runAs != null) {
                m.put(INTERNAL_RUN_AS_KEY, runAs);
            }

            resume(processKey, f.getEventName(), m);
        };

        Map<String, Object> merged = merge(form, data);

        // optionally save the user who submitted the form
        boolean saveSubmittedBy = getBoolean(form.getOptions(), InternalConstants.Forms.SAVE_SUBMITTED_BY_KEY, false);
        if (saveSubmittedBy) {
            merged.put(InternalConstants.Forms.SUBMITTED_BY_KEY, ldapManager.getCurrentUserInfo());
        }

        try {
            FormValidator validator = createFormValidator(processKey, formName);
            return toResult(processKey, form, DefaultFormService.submit(resumeHandler, validator, form, merged));
        } catch (ExecutionException e) {
            throw new ProcessException(processKey, "Form submit error: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public FormSubmitResult submitNext(ProcessKey processKey, Map<String, Object> data) {
        List<FormListEntry> forms;
        try {
            forms = list(processKey);
        } catch (Exception e) {
            throw new ProcessException(processKey, "Process execution error", e);
        }

        if (forms == null || forms.isEmpty()) {
            throw new ProcessException(processKey, "Invalid process state: no forms found");
        }

        FormSubmitResult lastResult = null;
        for (FormListEntry f : forms) {
            Map<String, Object> args = (Map<String, Object>) data.get(f.getName());

            lastResult = submit(processKey, f.getName(), args);
            if (!lastResult.isValid()) {
                return lastResult;
            }
        }
        return lastResult;
    }

    public boolean exists(PartialProcessKey processKey, String path) {
        return stateManager.exists(processKey, path);
    }

    private static FormSubmitResult toResult(PartialProcessKey processKey, Form f, io.takari.bpm.form.FormSubmitResult r) {
        return new FormSubmitResult(processKey.getInstanceId(), f.getFormDefinition().getName(), r.getErrors());
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

        Map<String, Object> options = form.getOptions();
        Map<String, Object> extraValues = options != null ? (Map<String, Object>) options.get("values") : null;

        // merge the initial form values and the "extra" values, provided
        // in the "values" option of the form
        Map<String, Object> a = new HashMap<>(formState);
        Map<String, Object> b = new HashMap<>(extraValues != null ? extraValues : Collections.emptyMap());
        ConfigurationUtils.merge(a, b);

        // overwrite the collected values with the submitted data
        Map<String, Object> c = new HashMap<>(data != null ? data : Collections.emptyMap());
        ConfigurationUtils.merge(a, c);

        return a;
    }

    private static Map<String, Object> removeReadonly(Form form, Map<String, Object> data) {
        FormDefinition fd = form.getFormDefinition();
        if (fd.getFields() == null) {
            return data;
        }

        fd.getFields().forEach(f -> {
            Boolean isReadOnly = f.getOption(READ_ONLY);
            if (Boolean.TRUE.equals(isReadOnly)) {
                data.remove(f.getName());
            }
        });

        return data;
    }

    private void resume(ProcessKey processKey, String eventName, Map<String, Object> req) throws ExecutionException {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            throw new ExecutionException("Error while creating a payload for: " + processKey, e);
        }

        resumePipeline.process(payload);
    }

    private static boolean getBoolean(Map<String, Object> m, String key, boolean defaultValue) {
        if (m == null) {
            return defaultValue;
        }

        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (!(v instanceof Boolean)) {
            throw new IllegalArgumentException("Expected a boolean value '" + key + "', got: " + v);
        }

        return (Boolean) v;
    }

    private FormValidator createFormValidator(ProcessKey processKey, String formName) {
        FormValidatorLocale locale = new ExternalFileFormValidatorLocale(processKey, formName, stateManager);
        return new ConcordFormValidator(locale);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getMap(Map<String, Object> m, String key, Map<String, Object> defaultValue) {
        if (m == null) {
            return defaultValue;
        }

        Object v = m.get(key);
        if (v == null) {
            return defaultValue;
        }

        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a map value '" + key + "', got: " + v);
        }

        return (Map<String, Object>) v;
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
