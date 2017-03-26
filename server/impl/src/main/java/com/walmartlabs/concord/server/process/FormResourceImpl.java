package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.FormInstanceEntry;
import com.walmartlabs.concord.server.api.process.FormListEntry;
import com.walmartlabs.concord.server.api.process.FormResource;
import com.walmartlabs.concord.server.api.process.FormSubmitResponse;
import io.takari.bpm.api.ExecutionException;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
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

    @Inject
    public FormResourceImpl(ConcordFormService formService) {
        this.formService = formService;
    }

    @Override
    @Validate
    public List<FormListEntry> list(String processInstanceId) {
        try {
            return formService.list(processInstanceId);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Error retrieving a list of forms: " + processInstanceId, e);
        }
    }

    @Override
    @Validate
    public FormInstanceEntry get(String processInstanceId, String formInstanceId) {
        Form form = formService.get(processInstanceId, formInstanceId);
        if (form == null) {
            throw new WebApplicationException("Form not found: " + formInstanceId, Status.NOT_FOUND);
        }

        FormDefinition fd = form.getFormDefinition();

        Map<String, Object> env = form.getEnv();

        Map<String, Object> data = env != null ? (Map<String, Object>) env.get(fd.getName()) : Collections.emptyMap();
        if (data == null) {
            data = Collections.emptyMap();
        }

        List<FormInstanceEntry.Field> fields = new ArrayList<>();
        for (FormField f : fd.getFields()) {
            FormInstanceEntry.Cardinatity c = map(f.getCardinality());
            String type = f.getType();
            Object value = data.get(f.getName());
            fields.add(new FormInstanceEntry.Field(f.getName(), f.getLabel(), type, c, value));
        }

        String pbk = form.getProcessBusinessKey();
        String fiid = form.getFormInstanceId().toString();
        String name = fd.getName(); // TODO description?
        return new FormInstanceEntry(pbk, fiid, name, fields);
    }

    private static FormInstanceEntry.Cardinatity map(FormField.Cardinality c) {
        if (c == null) {
            return null;
        }

        switch (c) {
            case ANY:
                return FormInstanceEntry.Cardinatity.ANY;
            case AT_LEAST_ONE:
                return FormInstanceEntry.Cardinatity.AT_LEAST_ONE;
            case ONE_AND_ONLY_ONE:
                return FormInstanceEntry.Cardinatity.ONE_AND_ONLY_ONE;
            case ONE_OR_NONE:
                return FormInstanceEntry.Cardinatity.ONE_OR_NONE;
            default:
                throw new IllegalArgumentException("Unsupported cardinality type: " + c);
        }
    }

    @Override
    @Validate
    public FormSubmitResponse submit(String processInstanceId, String formInstanceId, Map<String, Object> data) {
        FormSubmitResult result;
        try {
            result = formService.submit(processInstanceId, formInstanceId, data);
        } catch (ExecutionException e) {
            throw new WebApplicationException("Error submitting a form", e);
        }

        Map<String, String> errors = mergeErrors(result.getErrors());
        return new FormSubmitResponse(result.getProcessBusinessKey(), errors);
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
