package com.walmartlabs.concord.runner.engine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.common.form.ConcordFormValidator;
import com.walmartlabs.concord.project.yaml.YamlConverterException;
import com.walmartlabs.concord.project.yaml.YamlFormConverter;
import io.takari.bpm.api.ExecutionContextFactory;
import io.takari.bpm.form.DefaultFormService;
import io.takari.bpm.form.FormStorage;
import io.takari.bpm.model.form.FormField;

import java.util.Map;

public class ConcordFormService extends DefaultFormService {

    public ConcordFormService(ExecutionContextFactory contextFactory, ResumeHandler resumeHandler, FormStorage formStorage) {
        super(contextFactory, resumeHandler, formStorage, new ConcordFormValidator());
    }

    @Override
    @SuppressWarnings("unchecked")
    public FormField toFormField(Map<String, Object> m) {
        if (m.size() != 1) {
            throw new IllegalArgumentException("Expected a form field definition, got: " + m);
        }

        Map.Entry<String, Object> entry = m.entrySet().iterator().next();

        String name = entry.getKey();

        Object v = entry.getValue();
        if (!(v instanceof Map)) {
            throw new IllegalArgumentException("Expected a form field definition, got: " + m);
        }

        Map<String, Object> opts = (Map<String, Object>) v;

        try {
            return YamlFormConverter.convert(name, opts, null);
        } catch (YamlConverterException e) {
            throw new RuntimeException(e);
        }
    }
}
