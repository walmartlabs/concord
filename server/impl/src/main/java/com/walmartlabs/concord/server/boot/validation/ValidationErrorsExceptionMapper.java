package com.walmartlabs.concord.server.boot.validation;

/*
 * Copyright (c) 2007-2014 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 *
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.validation.ValidationErrorXO;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;

import javax.ws.rs.ext.Provider;
import java.util.List;

/**
 * Based on the original {@link org.sonatype.siesta.server.validation.ValidationErrorsExceptionMapper}.
 */
@Provider
public class ValidationErrorsExceptionMapper extends ValidationExceptionMapperSupport<ValidationErrorsException> {

    @Override
    protected List<ValidationErrorXO> getValidationErrors(ValidationErrorsException exception) {
        return exception.getValidationErrors();
    }
}
