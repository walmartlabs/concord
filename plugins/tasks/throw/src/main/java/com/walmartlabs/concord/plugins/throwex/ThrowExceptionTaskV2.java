package com.walmartlabs.concord.plugins.throwex;

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

import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import javax.inject.Named;
import java.io.Serializable;

@Named("throw")
public class ThrowExceptionTaskV2 implements Task {

    @Override
    public Serializable execute(Variables input) throws Exception {
        Object exception = input.get("exception");

        if (exception instanceof Exception) {
            throw (Exception) exception;
        } else if (exception instanceof String) {
            throw new UserDefinedException(exception.toString());
        } else if (exception instanceof Serializable) {
            throw new ConcordException("Process Error", (Serializable) exception);
        } else {
            throw new UserDefinedException(exception != null ? exception.toString() : "n/a");
        }
    }
}
