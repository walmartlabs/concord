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

import com.walmartlabs.concord.forms.FormUtils;
import com.walmartlabs.concord.forms.*;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.*;
import com.walmartlabs.concord.server.process.pipelines.ResumePipeline;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.user.UserInfoProvider.UserInfo;
import com.walmartlabs.concord.server.user.UserManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.*;

@Named
@Singleton
public class FormServiceV2 {

    public static final String FORMS_RESOURCES_PATH = "forms";
    public static final String INTERNAL_RUN_AS_KEY = "_runAs";

    private final PayloadManager payloadManager;
    private final ProcessStateManager stateManager;
    private final UserManager userManager;
    private final FormAccessManager formAccessManager;
    private final Chain resumePipeline;
    private final FormManager formManager;

    @Inject
    public FormServiceV2(
            PayloadManager payloadManager,
            ProcessStateManager stateManager,
            UserManager userManager, FormAccessManager formAccessManager,
            ResumePipeline resumePipeline,
            FormManager formManager) {

        this.payloadManager = payloadManager;
        this.stateManager = stateManager;
        this.userManager = userManager;
        this.formAccessManager = formAccessManager;
        this.resumePipeline = resumePipeline;
        this.formManager = formManager;
    }

    public Form get(PartialProcessKey processKey, String formName) {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return get(new ProcessKey(processKey, createdAt), formName);
    }

    public Form get(ProcessKey processKey, String formName) {
        Form form = formManager.get(processKey, formName);
        if (form == null) {
            return null;
        }

        formAccessManager.assertFormAccess(formName, form.options().runAs());

        return form;
    }

    public List<FormListEntry> list(PartialProcessKey processKey) {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return list(new ProcessKey(processKey, createdAt));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<FormListEntry> list(ProcessKey processKey) {
        List<Form> forms = formManager.list(processKey);
        List<FormListEntry> result = new ArrayList<>();
        for (Form f : forms) {
            String name = f.name();
            String s = FORMS_RESOURCES_PATH + "/" + f.name();
            boolean branding = stateManager.exists(processKey, s);
            Map runAs = f.options().runAs();

            result.add(new FormListEntry(name, branding, f.options().yield(), runAs));
        }
        return result;
    }

    public FormSubmitResult submit(PartialProcessKey processKey, String formName, Map<String, Object> data) throws FormUtils.ValidationException {
        Timestamp createdAt = stateManager.assertCreatedAt(processKey);
        return submit(new ProcessKey(processKey, createdAt), formName, data);
    }

    public FormSubmitResult submit(ProcessKey processKey, String formName, Map<String, Object> data) throws FormUtils.ValidationException {
        Form form = get(processKey, formName);
        if (form == null) {
            throw new ProcessException(processKey, "Form not found: " + formName);
        }

        FormValidatorLocale locale = new ExternalFileFormValidatorLocaleV2(processKey, formName, stateManager);

        data = new LinkedHashMap<>(FormUtils.convert(locale, form, data));

        // optionally save the user who submitted the form
        boolean saveSubmittedBy = form.options().saveSubmittedBy();
        if (saveSubmittedBy) {
            UserInfo i = userManager.getCurrentUserInfo();
            data.put(Constants.Forms.SUBMITTED_BY_KEY, i);
        }

        try {
            FormValidator validator = new DefaultFormValidator(locale);
            List<ValidationError> errors = validator.validate(form, data);
            if (errors != null && !errors.isEmpty()) {
                return new FormSubmitResult(processKey.getInstanceId(), form.name(), errors);
            }

            // the new form's values will be available under the form's name key
            Map<String, Object> args = new LinkedHashMap<>();
            args.put(form.name(), new LinkedHashMap<>(data));

            formManager.delete(processKey, form.name());

            // TODO refactor into the process manager
            Map<String, Object> m = new HashMap<>();
            m.put(Constants.Request.ARGUMENTS_KEY, args);
            m.put(Constants.Files.FORM_FILES, data.remove(Constants.Files.FORM_FILES));

            Object runAs = form.options().runAs();
            if (runAs != null) {
                m.put(INTERNAL_RUN_AS_KEY, runAs);
            }

            resume(processKey, form.eventName(), m);

            return new FormSubmitResult(processKey.getInstanceId(), form.name(), null);
        } catch (Exception e) {
            throw new ProcessException(processKey, "Form submit error: " + e.getMessage(), e);
        }
    }

    public boolean exists(PartialProcessKey processKey, String path) {
        return stateManager.exists(processKey, path);
    }

    public String nextFormId(ProcessKey processKey) {
        return formManager.nextFormId(processKey);
    }

    private void resume(ProcessKey processKey, String eventName, Map<String, Object> req) {
        Payload payload;
        try {
            payload = payloadManager.createResumePayload(processKey, eventName, req);
        } catch (IOException e) {
            throw new RuntimeException("Error while creating a payload for: " + processKey, e);
        }

        resumePipeline.process(payload);
    }
}
