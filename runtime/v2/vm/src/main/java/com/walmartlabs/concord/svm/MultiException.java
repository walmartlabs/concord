package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.util.Collection;
import java.util.stream.Collectors;

public class MultiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Collection<Exception> causes;

    public MultiException(Collection<Exception> causes) {
        super("Errors: " + toMessage(causes));
        this.causes = causes;
    }

    public Collection<Exception> getCauses() {
        return causes;
    }

    private static String toMessage(Collection<Exception> causes) {
        return causes.stream().map(Throwable::getMessage).collect(Collectors.joining("\n"));
    }
}
