package com.walmartlabs.concord.server.process;

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
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.process.ConcordFormService.FormSubmitResult;
import com.walmartlabs.concord.server.process.FormUtils.ValidationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.Resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.*;

@Named
@Singleton
@Api(value = "Process Forms", authorizations = {@Authorization("api_key"), @Authorization("session_key"), @Authorization("ldap")})
@Path("/api/v1/process")
public class FormResource implements Resource {

    private static final String FORMS_RESOURCES_PATH = "forms";

    private final ConcordFormService formService;
    private final FormValidatorLocale validatorLocale;

    @Inject
    public FormResource(ConcordFormService formService) {
        this.formService = formService;
        this.validatorLocale = new DefaultFormValidatorLocale();
    }

    @GET
    @ApiOperation(value = "List the available forms", responseContainer = "list", response = FormListEntry.class)
    @Path("/{processInstanceId}/form")
    @Produces(MediaType.APPLICATION_JSON)
    public List<FormListEntry> list(@ApiParam @PathParam("processInstanceId") UUID processInstanceId) {
        PartialProcessKey processKey = PartialProcessKey.from(processInstanceId);

        try {
            return formService.list(processKey);
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while retrieving a list of forms: " + processKey, e);
        }
    }

    /**
     * Return the current state of a form instance.
     *
     * @param formName
     * @return
     */
    @GET
    @ApiOperation("Get the current state of a form")
    @Path("/{processInstanceId}/form/{formName}")
    @Produces(MediaType.APPLICATION_JSON)
    @SuppressWarnings("unchecked")
    public FormInstanceEntry get(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                                 @ApiParam @PathParam("formName") String formName) {

        PartialProcessKey processKey = new PartialProcessKey(processInstanceId);

        Form form = formService.get(processKey, formName);
        if (form == null) {
            throw new ConcordApplicationException("Form " + formName + " not found. Process ID: " + processKey, Status.NOT_FOUND);
        }

        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> env = form.getEnv();

        Map<String, Object> data = env != null ? (Map<String, Object>) env.get(fd.getName()) : Collections.emptyMap();
        if (data == null) {
            data = new HashMap<>();
        }

        Map<String, Object> extra = null;
        boolean yield = false;
        Map<String, Object> opts = form.getOptions();
        if (opts != null) {
            extra = (Map<String, Object>) opts.get("values");
            yield = (boolean) opts.getOrDefault("yield", false);
        }

        if (extra != null) {
            data = ConfigurationUtils.deepMerge(data, extra);
        }

        Map<String, Object> allowedValues = form.getAllowedValues();
        if (allowedValues == null) {
            allowedValues = Collections.emptyMap();
        }

        List<FormInstanceEntry.Field> fields = new ArrayList<>();
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
     *
     * @param formName
     * @param data
     * @return
     */
    @POST
    @ApiOperation("Submit JSON form data")
    @Path("/{processInstanceId}/form/{formName}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public FormSubmitResponse submit(@ApiParam @PathParam("processInstanceId") UUID processInstanceId,
                                     @ApiParam @PathParam("formName") String formName,
                                     @ApiParam Map<String, Object> data) {

        PartialProcessKey processKey = new PartialProcessKey(processInstanceId);

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

        Map<String, String> errors = mergeErrors(result.getErrors());
        return new FormSubmitResponse(result.getProcessInstanceId(), errors);
    }

    /**
     * Submit form instance's data, potentially resuming a suspended process.
     * It's not annotated with Swagger's {@link ApiOperation} to avoid conflicts.
     *
     * @param formName
     * @param data
     * @return
     */
    @POST
    @Path("/{processInstanceId}/form/{formName}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public FormSubmitResponse submit(@PathParam("processInstanceId") UUID processInstanceId,
                                     @PathParam("formName") String formName,
                                     MultipartInput data) {

        return submit(processInstanceId, formName, MultipartUtils.toMap(data));
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

    private static Map<String, String> mergeErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }

        // TODO merge multiple errors
        Map<String, String> m = new HashMap<>();
        for (ValidationError e : errors) {
            m.put(e.getFieldName(), e.getError());
        }
        return m;
    }
}
