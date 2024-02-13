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

import com.walmartlabs.concord.common.form.ConcordFormValidatorLocale;
import com.walmartlabs.concord.common.form.DefaultConcordFormValidatorLocale;
import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.process.form.FormUtils.ValidationException;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.takari.bpm.form.Form;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.*;

@Tag(name = "FormsV1")
public class FormResourceV1 {

    private static final String FORMS_RESOURCES_PATH = "forms";

    private final FormServiceV1 formService;
    private final ConcordFormValidatorLocale validatorLocale;

    @Inject
    public FormResourceV1(FormServiceV1 formService) {
        this.formService = formService;
        this.validatorLocale = new DefaultConcordFormValidatorLocale();
    }

    @GET
    @Path("/{processInstanceId}/form")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "List the available forms", operationId = "listFormsV1")
    public List<FormListEntry> list(@PathParam("processInstanceId") UUID processInstanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        try {
            return formService.list(processKey);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while retrieving a list of forms: " + processKey, e);
        }
    }

    /**
     * Return the current state of a form instance.
     */
    @GET
    @Path("/{processInstanceId}/form/{formName}")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Get the current state of a form", operationId = "getFormV1")
    public FormInstanceEntry get(@PathParam("processInstanceId") UUID processInstanceId,
                                 @PathParam("formName") String formName) {

        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        Form form = formService.get(processKey, formName);
        if (form == null) {
            throw new ConcordApplicationException("Form " + formName + " not found. Process ID: " + processKey, Status.NOT_FOUND);
        }

        Map<String, Object> data = FormUtils.values(form);
        boolean yield = MapUtils.getBoolean(form.getOptions(), "yield", false);

        Map<String, Object> allowedValues = form.getAllowedValues();
        if (allowedValues == null) {
            allowedValues = Collections.emptyMap();
        }

        List<FormInstanceEntry.Field> fields = new ArrayList<>();
        FormDefinition fd = form.getFormDefinition();
        for (FormField f : fd.getFields()) {
            String fieldName = f.getName();

            FormInstanceEntry.Cardinality c = map(f.getCardinality());
            String type = f.getType();

            Object value = data.get(fieldName);
            Object allowedValue = allowedValues.get(fieldName);

            fields.add(new FormInstanceEntry.Field(fieldName, f.getLabel(), type, c, value, allowedValue, f.getOptions()));
        }

        String pbk = form.getProcessBusinessKey();
        String name = fd.getName();
        String resourcePath = FORMS_RESOURCES_PATH + "/" + name;
        boolean isCustomForm = formService.exists(processKey, resourcePath);

        return new FormInstanceEntry(pbk, name, fields, isCustomForm, yield);
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     */
    @POST
    @Path("/{processInstanceId}/form/{formName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(description = "Submit JSON form data", operationId = "submitFormV1")
    public FormSubmitResponse submit(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formName") String formName,
                                     Map<String, Object> data) {

        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        Form form = formService.get(processKey, formName);
        if (form == null) {
            throw new ConcordApplicationException("Form " + formName + " not found. Process ID: " + processInstanceId, Status.NOT_FOUND);
        }

        try {
            data = FormUtils.convert(validatorLocale, form, data);
        } catch (ValidationException e) {
            Map<String, String> errors = Collections.singletonMap(e.getField().getName(), e.getMessage());
            return new FormSubmitResponse(processInstanceId, errors);
        }

        FormSubmitResult result = formService.submit(processKey, formName, data);

        Map<String, String> errors = FormUtils.mergeErrors(result.getErrors());
        return new FormSubmitResponse(result.getProcessInstanceId(), errors);
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     * The method must have a different {@code @Path} than {@link #submit(UUID, String, Map)} to avoid
     * conflicts in the Swagger spec/clients.
     */
    @POST
//    @ApiOperation(value = "Submit multipart form data")
    @Path("/{processInstanceId}/form/{formName}/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public FormSubmitResponse submit(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formName") String formName,
                                     MultipartInput data) {

        Map<String, Object> m = MultipartUtils.toMap(data);
        return submit(processInstanceId, formName, m);
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
