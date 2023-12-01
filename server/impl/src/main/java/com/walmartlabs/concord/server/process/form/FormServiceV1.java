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
import com.walmartlabs.concord.forms.ValidationError;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.PayloadManager;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessManager;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.user.UserInfoProvider.UserInfo;
import com.walmartlabs.concord.server.user.UserManager;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormService;
import io.takari.bpm.form.DefaultFormService.ResumeHandler;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormValidator;
import io.takari.bpm.form.FormValidatorLocale;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;

public class FormServiceV1 {

    public static final String FORMS_RESOURCES_PATH = "forms";
    public static final String INTERNAL_RUN_AS_KEY = "_runAs";

    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final UserManager userManager;
    private final FormAccessManager formAccessManager;
    private final ProcessManager processManager;
    private final ProcessKeyCache processKeyCache;

    @Inject
    public FormServiceV1(PayloadManager payloadManager,
                         ProcessStateManager stateManager,
                         UserManager userManager,
                         FormAccessManager formAccessManager,
                         ProcessManager processManager,
                         ProcessKeyCache processKeyCache) {

        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.userManager = userManager;
        this.formAccessManager = formAccessManager;
        this.processManager = processManager;
        this.processKeyCache = processKeyCache;
    }

    public Form get(PartialProcessKey partialProcessKey, String formName) {
        ProcessKey processKey = processKeyCache.assertKey(partialProcessKey.getInstanceId());
        return get(processKey, formName);
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

    public List<FormListEntry> list(PartialProcessKey partialProcessKey) {
        ProcessKey processKey = processKeyCache.assertKey(partialProcessKey.getInstanceId());
        return list(processKey);
    }

    public List<FormListEntry> list(ProcessKey processKey) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.JOB_FORMS_DIR_NAME);

        List<Form> forms = stateManager.forEach(processKey, resource, FormServiceV1::deserialize);
        return forms.stream().map(f -> {
            String name = f.getFormDefinition().getName();

            String s = FORMS_RESOURCES_PATH + "/" + f.getFormDefinition().getName();
            boolean branding = stateManager.exists(processKey, s);

            Map<String, Object> opts = f.getOptions();
            boolean yield = MapUtils.getBoolean(opts, "yield", false);
            Map<String, Object> runAs = MapUtils.getMap(opts, Constants.Forms.RUN_AS_KEY, null);

            return new FormListEntry(name, branding, yield, runAs);
        }).collect(Collectors.toList());
    }

    public String nextFormId(ProcessKey processKey) {
        String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                Constants.Files.JOB_STATE_DIR_NAME,
                Constants.Files.JOB_FORMS_DIR_NAME);

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

    public FormSubmitResult submit(PartialProcessKey partialProcessKey, String formName, Map<String, Object> data) {
        ProcessKey processKey = processKeyCache.assertKey(partialProcessKey.getInstanceId());
        return submit(processKey, formName, data);
    }

    public FormSubmitResult submit(ProcessKey processKey, String formName, Map<String, Object> data) {
        Form form = get(processKey, formName);
        if (form == null) {
            throw new ProcessException(processKey, "Form not found: " + formName);
        }

        ResumeHandler resumeHandler = (f, args) -> {
            String resource = path(Constants.Files.JOB_ATTACHMENTS_DIR_NAME,
                    Constants.Files.JOB_STATE_DIR_NAME,
                    Constants.Files.JOB_FORMS_DIR_NAME,
                    formName);

            stateManager.deleteFile(processKey, resource);

            @SuppressWarnings("unchecked")
            Map<String, Object> clearedData = (Map<String, Object>) args.get(f.getFormDefinition().getName());

            args.put(f.getFormDefinition().getName(), clearedData);

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put(Constants.Request.ARGUMENTS_KEY, args);
            if (data != null) {
                m.put(Constants.Files.FORM_FILES, data.remove(Constants.Files.FORM_FILES));
            }

            Map<String, Object> opts = f.getOptions();
            Object runAs = opts != null ? opts.get(Constants.Forms.RUN_AS_KEY) : null;
            if (runAs != null) {
                m.put(INTERNAL_RUN_AS_KEY, runAs);
            }

            resume(processKey, f.getEventName(), m);
        };

        Map<String, Object> merged = merge(form, data);

        // optionally save the user who submitted the form
        boolean saveSubmittedBy = MapUtils.getBoolean(form.getOptions(), Constants.Forms.SAVE_SUBMITTED_BY_KEY, false);
        if (saveSubmittedBy) {
            UserInfo i = userManager.getCurrentUserInfo();
            merged.put(Constants.Forms.SUBMITTED_BY_KEY, i);
        }

        try {
            FormValidator validator = createFormValidator(processKey, formName);
            return toResult(processKey, form, DefaultFormService.submit(resumeHandler, validator, form, merged));
        } catch (ExecutionException e) {
            throw new ProcessException(processKey, "Form submit error: " + e.getMessage(), e);
        }
    }

    public boolean exists(PartialProcessKey processKey, String path) {
        return stateManager.exists(processKey, path);
    }

    private static FormSubmitResult toResult(PartialProcessKey processKey, Form f, io.takari.bpm.form.FormSubmitResult r) {
        return new FormSubmitResult(processKey.getInstanceId(), f.getFormDefinition().getName(), convert(r.getErrors()));
    }

    private static List<ValidationError> convert(List<io.takari.bpm.form.FormSubmitResult.ValidationError> errors) {
        if (errors == null) {
            return null;
        }

        List<ValidationError> result = new ArrayList<>();
        for (io.takari.bpm.form.FormSubmitResult.ValidationError e : errors) {
            result.add(ValidationError.of(e.getFieldName(), e.getError()));
        }
        return result;
    }

    private static Map<String, Object> merge(Form form, Map<String, Object> data) {
        Map<String, Object> a = FormUtils.values(form);

        // overwrite the collected values with the submitted data
        Map<String, Object> c = new HashMap<>(data != null ? data : Collections.emptyMap());
        ConfigurationUtils.merge(a, c);

        return a;
    }

    private void resume(ProcessKey processKey, String eventName, Map<String, Object> req) throws ExecutionException {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            throw new ExecutionException("Error while creating a payload for: " + processKey, e);
        }

        processManager.resume(payload);
    }

    private FormValidator createFormValidator(ProcessKey processKey, String formName) {
        FormValidatorLocale locale = new ExternalFileFormValidatorLocale(processKey, formName, stateManager);
        return new ConcordFormValidator(locale);
    }
}
