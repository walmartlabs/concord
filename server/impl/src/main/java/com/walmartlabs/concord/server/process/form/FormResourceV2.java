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

import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.forms.FormField;
import com.walmartlabs.concord.forms.FormUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;
import java.io.Serializable;
import java.util.*;

public class FormResourceV2 {

    private static final String FORMS_RESOURCES_PATH = "forms";

    private final FormServiceV2 formService;

    @Inject
    public FormResourceV2(FormServiceV2 formService) {
        this.formService = formService;
    }

    /**
     * Return the current state of a form instance.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public FormInstanceEntry get(UUID processInstanceId, String formName) {

        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        Form form = formService.get(processKey, formName);
        if (form == null) {
            throw new ConcordApplicationException("Form " + formName + " not found. Process ID: " + processKey, Status.NOT_FOUND);
        }

        List<FormInstanceEntry.Field> fields = new ArrayList<>();
        for (FormField f : form.fields()) {
            String fieldName = f.name();

            FormInstanceEntry.Cardinality c = map(f.cardinality());
            String type = f.type();

            Serializable value = f.defaultValue();
            Serializable allowedValue = f.allowedValue();
            Map options = f.options();

            fields.add(new FormInstanceEntry.Field(fieldName, f.label(), type, c, value, allowedValue, options));
        }

        String name = form.name();
        boolean yield = form.options().isYield();
        String resourcePath = FORMS_RESOURCES_PATH + "/" + name;
        boolean isCustomForm = formService.exists(processKey, resourcePath);

        return new FormInstanceEntry(processInstanceId.toString(), name, fields, isCustomForm, yield);
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     */
    public FormSubmitResponse submit(UUID processInstanceId, String formName, Map<String, Object> data) {
        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        try {
            FormSubmitResult result = formService.submit(processKey, formName, data);

            Map<String, String> errors = com.walmartlabs.concord.server.process.form.FormUtils.mergeErrors(result.getErrors());
            return new FormSubmitResponse(result.getProcessInstanceId(), errors);
        } catch (FormUtils.ValidationException e) {
            Map<String, String> errors = Collections.singletonMap(e.getField().name(), e.getMessage());
            return new FormSubmitResponse(processInstanceId, errors);
        }
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     * The method must have a different {@code @Path} than {@link #submit(UUID, String, Map)} to avoid
     * conflicts in the Swagger spec/clients.
     */
    public FormSubmitResponse submit(UUID processInstanceId, String formName, MultipartInput data) {

        Map<String, Object> m = MultipartUtils.toMap(data);
        return submit(processInstanceId, formName, m);
    }

    public List<FormListEntry> list(UUID processInstanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        try {
            return formService.list(processKey);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while retrieving a list of forms: " + processKey, e);
        }
    }

    private static FormInstanceEntry.Cardinality map(FormField.Cardinality c) {
        if (c == null) {
            return null;
        }

        switch (c) {
            case ANY:
                return FormInstanceEntry.Cardinality.ANY;
            case AT_LEAST_ONE:
                return FormInstanceEntry.Cardinality.AT_LEAST_ONE;
            case ONE_AND_ONLY_ONE:
                return FormInstanceEntry.Cardinality.ONE_AND_ONLY_ONE;
            case ONE_OR_NONE:
                return FormInstanceEntry.Cardinality.ONE_OR_NONE;
            default:
                throw new IllegalArgumentException("Unsupported cardinality type: " + c);
        }
    }
}
