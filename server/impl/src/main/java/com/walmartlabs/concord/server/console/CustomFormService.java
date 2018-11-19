package com.walmartlabs.concord.server.console;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.walmartlabs.concord.common.ConfigurationUtils;
import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.MultipartUtils;
import com.walmartlabs.concord.server.cfg.CustomFormConfiguration;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.ProcessStatus;
import com.walmartlabs.concord.server.process.form.ConcordFormService;
import com.walmartlabs.concord.server.process.form.ConcordFormService.FormSubmitResult;
import com.walmartlabs.concord.server.process.form.ExternalFileFormValidatorLocale;
import com.walmartlabs.concord.server.process.form.FormUtils;
import com.walmartlabs.concord.server.process.form.FormUtils.ValidationException;
import com.walmartlabs.concord.server.process.queue.ProcessKeyCache;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import io.takari.bpm.form.Form;
import io.takari.bpm.form.FormSubmitResult.ValidationError;
import io.takari.bpm.model.form.FormDefinition;
import io.takari.bpm.model.form.FormField;
import io.takari.bpm.model.form.FormField.Cardinality;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.siesta.Resource;
import org.sonatype.siesta.Validate;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.copyTo;

@Named
@Singleton
@javax.ws.rs.Path("/api/service/custom_form")
public class CustomFormService implements Resource {

    private static final Logger log = LoggerFactory.getLogger(CustomFormService.class);

    private static final String FORMS_PATH_PREFIX = "/forms/";
    private static final String FORM_DIR_NAME = "form";
    private static final String SHARED_DIR_NAME = "shared";
    private static final String FORMS_PATH_TEMPLATE = FORMS_PATH_PREFIX + "%s/%s/" + FORM_DIR_NAME + "/";
    private static final String DATA_FILE_TEMPLATE = "data = %s;";

    private static final String NON_BRANDED_FORM_URL_TEMPLATE = "/#/process/%s/form/%s?fullScreen=true&wizard=true";
    private static final String FORM_WIZARD_CONTINUE_URL_TEMPLATE = "/api/service/custom_form/%s/%s/continue";

    private static final long STATUS_REFRESH_DELAY = 250;

    private final CustomFormConfiguration cfg;
    private final ConcordFormService formService;
    private final ProcessStateManager stateManager;
    private final ProcessQueueDao queueDao;
    private final ProcessKeyCache processKeyCache;
    private final ObjectMapper objectMapper;

    @Inject
    public CustomFormService(CustomFormConfiguration cfg,
                             ConcordFormService formService,
                             ProcessStateManager stateManager,
                             ProcessQueueDao queueDao,
                             ProcessKeyCache processKeyCache) {

        this.cfg = cfg;
        this.formService = formService;
        this.stateManager = stateManager;
        this.queueDao = queueDao;
        this.processKeyCache = processKeyCache;

        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/start")
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public FormSessionResponse startSession(@PathParam("processInstanceId") UUID processInstanceId,
                                            @PathParam("formName") String formName) {

        ProcessKey processKey = assertKey(processInstanceId);
        return startSession(processKey, formName);
    }

    private FormSessionResponse startSession(ProcessKey processKey, String formName) {

        // TODO locking
        Form form = assertForm(processKey, formName);

        Path dst = cfg.getBaseDir()
                .resolve(processKey.toString())
                .resolve(formName);

        try {
            Path formDir = dst.resolve(FORM_DIR_NAME);
            if (!Files.exists(formDir)) {
                Files.createDirectories(formDir);
            }

            String resource = ConcordFormService.FORMS_RESOURCES_PATH + "/" + form.getFormDefinition().getName();
            // copy original branding files into the target directory
            boolean branded = stateManager.exportDirectory(processKey, resource, copyTo(formDir));
            if (!branded) {
                // not branded, redirect to the default wizard
                String uri = String.format(NON_BRANDED_FORM_URL_TEMPLATE, processKey, formName);
                return new FormSessionResponse(uri);
            }

            // create JS file containing the form's data
            writeData(formDir, initialData(form));

            // copy shared resources (if present)
            copySharedResources(processKey, dst);
        } catch (IOException e) {
            log.warn("startSession ['{}', '{}'] -> error while preparing a custom form: {}", processKey, formName, e);
            throw new ConcordApplicationException("Error while preparing a custom form", e);
        }

        return new FormSessionResponse(formPath(processKey, formName));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    @Validate
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultivaluedMap<String, String> data) {

        ProcessKey processKey = assertKey(processInstanceId);
        return continueSession(uriInfo, headers, processKey, formName, FormUtils.convert(data));
    }

    @POST
    @javax.ws.rs.Path("{processInstanceId}/{formName}/continue")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response continueSession(@Context UriInfo uriInfo,
                                    @Context HttpHeaders headers,
                                    @PathParam("processInstanceId") UUID processInstanceId,
                                    @PathParam("formName") String formName,
                                    MultipartInput data) {

        ProcessKey processKey = assertKey(processInstanceId);
        return continueSession(uriInfo, headers, processKey, formName, MultipartUtils.toMap(data));
    }

    private Response continueSession(UriInfo uriInfo, HttpHeaders headers, ProcessKey processKey,
                                     String formName, Map<String, Object> data) {
        // TODO locking
        Form form = assertForm(processKey, formName);

        // TODO constants
        Map<String, Object> opts = form.getOptions();
        boolean yield = opts != null && (boolean) opts.getOrDefault("yield", false);

        Path dst = cfg.getBaseDir()
                .resolve(processKey.toString())
                .resolve(formName);

        Path formDir = dst.resolve(FORM_DIR_NAME);

        try {
            Map<String, Object> m = new HashMap<>();
            try {
                m = FormUtils.convert(new ExternalFileFormValidatorLocale(processKey, formName, stateManager), form, data);

                FormSubmitResult r = formService.submit(processKey, formName, m);
                if (r.isValid()) {
                    if (yield) {
                        // this was the last "interactive" form. The process will continue in "background"
                        // and users should get a success page.
                        writeData(formDir, success(form, m));
                    } else {
                        while (true) {
                            ProcessEntry entry = queueDao.get(processKey);
                            ProcessStatus s = entry.status();

                            if (s == ProcessStatus.SUSPENDED) {
                                String nextFormId = formService.nextFormId(processKey);
                                if (nextFormId == null) {
                                    writeData(formDir, success(form, m));
                                    break;
                                } else {
                                    FormSessionResponse nextSession = startSession(processKey, nextFormId);
                                    return redirectTo(nextSession.getUri());
                                }
                            } else if (s == ProcessStatus.FAILED || s == ProcessStatus.CANCELLED || s == ProcessStatus.TIMED_OUT) {
                                writeData(formDir, processFailed(form, m));
                                break;
                            } else if (s == ProcessStatus.FINISHED) {
                                writeData(formDir, success(form, m));
                                break;
                            }

                            try {
                                // TODO exp back off?
                                Thread.sleep(STATUS_REFRESH_DELAY);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                } else {
                    writeData(formDir, prepareData(form, m, r.getErrors()));
                }
            } catch (ValidationException e) {
                ValidationError err = new ValidationError(e.getField().getName(), e.getMessage());
                FormData d = prepareData(form, m, Collections.singletonList(err));
                writeData(formDir, d);
            }
        } catch (Exception e) {
            throw new ConcordApplicationException("Error while submitting a form", e);
        }

        return redirectToForm(uriInfo, headers, processKey, formName);
    }

    private Form assertForm(ProcessKey processKey, String formName) {
        Form form = formService.get(processKey, formName);
        if (form == null) {
            log.warn("assertForm ['{}', '{}'] -> not found", processKey, formName);
            throw new ConcordApplicationException("Form not found", Status.NOT_FOUND);
        }
        return form;
    }

    private FormData initialData(Form form) {
        return prepareData(false, false, form, null, false, null);
    }

    private FormData success(Form form, Map<String, Object> overrides) {
        return prepareData(true, false, form, overrides, false, null);
    }

    private FormData processFailed(Form form, Map<String, Object> overrides) {
        return prepareData(false, true, form, overrides, false, null);
    }

    private FormData prepareData(Form form, Map<String, Object> overrides, List<ValidationError> errors) {
        return prepareData(false, false, form, overrides, true, errors);
    }


    @SuppressWarnings("unchecked")
    private FormData prepareData(boolean success, boolean processFailed, Form form,
                                 Map<String, Object> overrides, boolean skipMissingOverrides,
                                 List<ValidationError> errors) {

        String processInstanceId = form.getProcessBusinessKey();
        String formName = form.getFormDefinition().getName();

        String submitUrl = String.format(FORM_WIZARD_CONTINUE_URL_TEMPLATE, processInstanceId, formName);

        // TODO merge with FormResource
        Map<String, FormDataDefinition> _definitions = new HashMap<>();

        // the order of precedence should be:
        //   submitted value > form call value > field's default value > environment value
        Map<String, Object> _values = new HashMap<>();
        Map<String, String> _errors = new HashMap<>();

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
            _values.putAll(extra);
        }

        Map<String, Object> allowedValues = form.getAllowedValues();
        if (allowedValues == null) {
            allowedValues = Collections.emptyMap();
        }

        for (FormField f : fd.getFields()) {
            Object allowedValue = allowedValues.get(f.getName());

            _definitions.put(f.getName(), new FormDataDefinition(f.getLabel(), f.getType(), f.getCardinality(), allowedValue));

            Object v = overrides != null ? overrides.get(f.getName()) : null;
            if (v == null && skipMissingOverrides) {
                continue;
            }

            if (v == null) {
                v = data.get(f.getName());
            }

            if (v == null) {
                continue;
            }

            _values.put(f.getName(), v);
        }

        if (errors != null) {
            for (ValidationError e : errors) {
                _errors.put(e.getFieldName(), e.getError());
            }
        }

        return new FormData(success, processFailed, submitUrl, _definitions, _values, _errors);
    }

    private void writeData(Path baseDir, Object data) throws IOException {
        Path dst = baseDir.resolve("data.js");

        Path parent = dst.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String s = String.format(DATA_FILE_TEMPLATE, objectMapper.writeValueAsString(data));
        Files.write(dst, s.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void copySharedResources(ProcessKey processKey, Path dst) throws IOException {
        Path sharedDir = dst.resolve(SHARED_DIR_NAME);
        if (!Files.exists(sharedDir)) {
            Files.createDirectories(sharedDir);
        }

        String resource = ConcordFormService.FORMS_RESOURCES_PATH + "/" + SHARED_DIR_NAME;
        stateManager.exportDirectory(processKey, resource, copyTo(sharedDir));
    }

    private ProcessKey assertKey(UUID processInstanceId) {
        Optional<ProcessKey> pk = processKeyCache.getUncached(processInstanceId);
        return pk.orElseThrow(() -> new ConcordApplicationException("Process not found: " + processInstanceId, Status.NOT_FOUND));
    }

    private static Response redirectToForm(UriInfo uriInfo, HttpHeaders headers,
                                           PartialProcessKey processKey, String formName) {

        String scheme = uriInfo.getBaseUri().getScheme();

        // check if we need to force https
        String origin = headers.getHeaderString("Origin");
        if (origin != null) {
            URI originUri = URI.create(origin);
            String originScheme = originUri.getScheme();
            if ("https".equalsIgnoreCase(originScheme)) {
                scheme = originScheme;
            }
        }

        UriBuilder b = UriBuilder.fromUri(uriInfo.getBaseUri())
                .scheme(scheme)
                .path(formPath(processKey, formName));

        return redirectTo(b.build().toString());
    }

    private static Response redirectTo(String path) {
        return Response.status(Status.MOVED_PERMANENTLY)
                .header(HttpHeaders.LOCATION, path)
                .build();
    }

    private static String formPath(PartialProcessKey processKey, String formName) {
        return String.format(FORMS_PATH_TEMPLATE, processKey, formName);
    }

    @JsonInclude(Include.NON_EMPTY)
    private static class FormData implements Serializable {

        private final boolean success;
        private final boolean processFailed;
        private final String submitUrl;
        private final Map<String, FormDataDefinition> definitions;
        private final Map<String, Object> values;
        private final Map<String, String> errors;

        public FormData(boolean success, boolean processFailed, String submitUrl,
                        Map<String, FormDataDefinition> definitions, Map<String, Object> values,
                        Map<String, String> errors) {

            this.success = success;
            this.processFailed = processFailed;
            this.submitUrl = submitUrl;
            this.definitions = definitions;
            this.values = values;
            this.errors = errors;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isProcessFailed() {
            return processFailed;
        }

        public String getSubmitUrl() {
            return submitUrl;
        }

        public Map<String, FormDataDefinition> getDefinitions() {
            return definitions;
        }

        public Map<String, Object> getValues() {
            return values;
        }

        public Map<String, String> getErrors() {
            return errors;
        }
    }

    @JsonInclude(Include.NON_NULL)
    private static class FormDataDefinition implements Serializable {

        private final String label;
        private final String type;
        private final Cardinality cardinality;
        private final Object allow;

        private FormDataDefinition(String label, String type, Cardinality cardinality, Object allow) {
            this.label = label;
            this.type = type;
            this.cardinality = cardinality;
            this.allow = allow;
        }

        public String getLabel() {
            return label;
        }

        public String getType() {
            return type;
        }

        public Cardinality getCardinality() {
            return cardinality;
        }

        public Object getAllow() {
            return allow;
        }
    }
}
