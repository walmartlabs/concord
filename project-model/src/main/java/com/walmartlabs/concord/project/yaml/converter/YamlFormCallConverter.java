package com.walmartlabs.concord.project.yaml.converter;

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

import com.fasterxml.jackson.core.JsonLocation;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.model.YamlFormCall;
import io.takari.bpm.model.UserTask;
import io.takari.bpm.model.form.FormExtension;

import java.util.*;

public class YamlFormCallConverter implements StepConverter<YamlFormCall> {

    private static final List<String> SUPPORTED_FORM_OPTIONS = Arrays.asList(InternalConstants.Forms.YIELD_KEY,
            InternalConstants.Forms.RUN_AS_KEY,
            InternalConstants.Forms.VALUES_KEY,
            InternalConstants.Forms.FIELDS_KEY,
            InternalConstants.Forms.SAVE_SUBMITTED_BY_KEY);

    @Override
    @SuppressWarnings("unchecked")
    public Chunk convert(ConverterContext ctx, YamlFormCall s) throws YamlConverterException {
        Chunk c = new Chunk();
        String id = ctx.nextId();

        Map<String, Object> opts = (Map<String, Object>) StepConverter.deepConvert(s.getOptions());
        if (opts != null && opts.isEmpty()) {
            opts = null;
        }

        validate(opts, s.getLocation());

        String formId = null;
        String formIdExpression = null;
        if (StepConverter.isExpression(s.getKey())) {
            formIdExpression = s.getKey();
        } else {
            formId = s.getKey();
        }

        c.addElement(new UserTask(id, new FormExtension(formId, formIdExpression, opts)));
        c.addOutput(id);
        c.addSourceMap(id, toSourceMap(s, "Form: " + s.getKey()));

        return c;
    }

    private static void validate(Map<String, Object> opts, JsonLocation loc) {
        if (opts == null) {
            return;
        }

        Set<String> keys = new HashSet<>(opts.keySet());
        keys.removeAll(SUPPORTED_FORM_OPTIONS);

        if (keys.isEmpty()) {
            return;
        }

        throw new IllegalArgumentException("'" + keys + "' are not supported options for a form. Supported options: "
                + SUPPORTED_FORM_OPTIONS + ". Error in a form step @:" + loc);
    }
}
