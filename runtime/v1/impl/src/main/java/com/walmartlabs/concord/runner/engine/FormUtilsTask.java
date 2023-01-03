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

import com.walmartlabs.concord.sdk.*;

import javax.inject.Named;
import java.util.Map;

import static com.walmartlabs.concord.sdk.Constants.Context.TX_ID_KEY;

@Named("forms")
public class FormUtilsTask implements Task {

    private static final String FORM_KEY = "form";
    private static final String WIZARD_KEY = "wizard";

    @InjectVariable("uiLinks")
    private Map<String, Object> defaults;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    public String getFormLink(String formName) {
        String instanceId = ContextUtils.assertString(context, TX_ID_KEY);
        return String.format(getTemplate(FORM_KEY), instanceId, formName);
    }

    public String getWizardLink() {
        String instanceId = ContextUtils.assertString(context, TX_ID_KEY);
        return String.format(getTemplate(WIZARD_KEY), instanceId);
    }

    private String getTemplate(String key) {
        String t = (String) (defaults != null ? defaults.get(key) : null);
        if (t == null) {
            throw new IllegalArgumentException("'uiLinks." + key + "' is undefined");
        }
        return t;
    }
}
