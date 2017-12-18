package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.api.process.FormInstanceEntry;
import com.walmartlabs.concord.server.api.process.FormListEntry;
import com.walmartlabs.concord.server.api.process.FormResource;
import com.walmartlabs.concord.server.api.process.FormSubmitResponse;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.process.ConcordFormService.FormSubmitResult;
import com.walmartlabs.concord.server.process.FormUtils.ValidationException;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.form.FormValidatorLocale;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;
import java.util.*;

@Named
public class FormResourceImpl implements FormResource, Resource {

    private final ConcordFormService formService;
    private final FormValidatorLocale validatorLocale;

    @Inject
    public FormResourceImpl(ConcordFormService formService) {
        this.formService = formService;
        this.validatorLocale = new DefaultFormValidatorLocale();
    }

    @Override
    @Validate
    public List<FormListEntry> list(UUID processInstanceId) {
        try {
            return formService.list(processInstanceId);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Error retrieving a list of forms: " + processInstanceId, e);
        }
    }

    @Override
    @Validate
    @WithTimer
    @SuppressWarnings("unchecked")
    public FormInstanceEntry get(UUID processInstanceId, String formInstanceId) {
        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new WebApplicationException("Form not found: " + formInstanceId, Status.NOT_FOUND);
        }

        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> env = form.getEnv();

        Map<String, Object> data = env != null ? (Map<String, Object>) env.get(fd.getName()) : Collections.emptyMap();
        if (data == null) {
            data = new HashMap<>();
        }

        Map<String, Object> extra = null;
        Map<String, Object> opts = form.getOptions();
        if (opts != null) {
            extra = (Map<String, Object>) opts.get("values");
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
        String fiid = form.getFormInstanceId().toString();
        String name = fd.getName();
        return new FormInstanceEntry(pbk, fiid, name, fields);
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

    @Override
    @Validate
    @WithTimer
    public FormSubmitResponse submit(UUID processInstanceId, String formInstanceId, Map<String, Object> data) {
        Form form = formService.get(processInstanceId, formInstanceId);
        try {
            data = FormUtils.convert(validatorLocale, form, data);
        } catch (ValidationException e) {
            Map<String, String> errors = Collections.singletonMap(e.getField().getName(), e.getMessage());
            return new FormSubmitResponse(processInstanceId, errors);
        }

        FormSubmitResult result = formService.submit(processInstanceId, formInstanceId, data);

        Map<String, String> errors = mergeErrors(result.getErrors());
        return new FormSubmitResponse(result.getProcessInstanceId(), errors);
    }

    @Override
    @Validate
    @WithTimer
    public FormSubmitResponse submit(UUID processInstanceId, String formInstanceId, MultipartInput data) {
        return submit(processInstanceId, formInstanceId, MultipartUtils.toMap(data));
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
