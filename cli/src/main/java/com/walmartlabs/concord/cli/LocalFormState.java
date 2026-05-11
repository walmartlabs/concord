package com.walmartlabs.concord.cli;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.forms.Form;
import com.walmartlabs.concord.runtime.common.FormService;
import com.walmartlabs.concord.sdk.Constants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class LocalFormState {

    static List<Form> syncPendingForms(Path workDir, Set<String> waitingEvents) throws IOException {
        var formsDir = formsDir(workDir);
        if (Files.notExists(formsDir)) {
            return List.of();
        }

        var forms = new FormService(formsDir).list();
        for (var form : forms) {
            if (!waitingEvents.contains(form.eventName())) {
                Files.deleteIfExists(formPath(workDir, form.name()));
            }
        }

        return forms.stream()
                .filter(form -> waitingEvents.contains(form.eventName()))
                .sorted(Comparator.comparing(Form::name).thenComparing(Form::eventName))
                .collect(Collectors.toList());
    }

    static void assertSupported(Path workDir, Collection<Form> forms) {
        for (var form : forms) {
            var customAssets = customAssetsPath(workDir, form.name());
            if (Files.exists(customAssets)) {
                throw new IllegalArgumentException("Custom form assets are not supported in local CLI resume: " + customAssets);
            }
        }
    }

    static Set<String> formEvents(Collection<Form> forms) {
        return forms.stream()
                .map(Form::eventName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Path formFilesDir(Path workDir) {
        return workDir.resolve(Constants.Files.FORM_FILES);
    }

    private static Path formsDir(Path workDir) {
        return workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .resolve(Constants.Files.JOB_STATE_DIR_NAME)
                .resolve(Constants.Files.JOB_FORMS_V2_DIR_NAME);
    }

    private static Path formPath(Path workDir, String formName) {
        return formsDir(workDir).resolve(formName);
    }

    private static Path customAssetsPath(Path workDir, String formName) {
        return workDir.resolve(Constants.Files.JOB_FORMS_DIR_NAME).resolve(formName);
    }

    private LocalFormState() {
    }
}
