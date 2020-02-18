package com.walmartlabs.concord.server.process.form;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.forms.ValidationError;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public final class FormSubmitResult implements Serializable {

    private final UUID processInstanceId;
    private final String formName;
    private final List<ValidationError> errors;

    public FormSubmitResult(UUID processInstanceId, String formName, List<ValidationError> errors) {
        this.processInstanceId = processInstanceId;
        this.formName = formName;
        this.errors = errors;
    }

    public UUID getProcessInstanceId() {
        return processInstanceId;
    }

    public String getFormName() {
        return formName;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public boolean isValid() {
        return errors == null || errors.isEmpty();
    }
}
