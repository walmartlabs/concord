package com.walmartlabs.concord.common.form;

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

import io.takari.bpm.form.DefaultFormValidatorLocale;
import io.takari.bpm.model.form.FormField;

public class DefaultConcordFormValidatorLocale extends DefaultFormValidatorLocale implements ConcordFormValidatorLocale {

    @Override
    public String expectedDate(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected a date value, got %s", fieldName(field, idx), value);
    }
}
