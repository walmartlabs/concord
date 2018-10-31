package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.project.InternalConstants;
import io.takari.bpm.api.ExecutionContext;
import io.takari.bpm.api.ExecutionContextFactory;
import io.takari.bpm.api.Variables;
import io.takari.bpm.context.ExecutionContextImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.el.PropertyNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ProcessOutVariables {

    private static final Logger log = LoggerFactory.getLogger(ProcessOutVariables.class);

    private final ExecutionContextFactory<? extends ExecutionContextImpl> contextFactory;

    public ProcessOutVariables(ExecutionContextFactory<? extends ExecutionContextImpl> contextFactory) {
        this.contextFactory = contextFactory;
    }

    public Map<String, Object> eval(Variables vars) {
        Collection<String> outExprs = getOutExpressions(vars);
        if (outExprs == null || outExprs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new HashMap<>();
        for (String x : outExprs) {
            ExecutionContext ctx = contextFactory.create(vars);

            Object v;
            try {
                v = ctx.eval("${" + x + "}", Object.class);
            } catch (PropertyNotFoundException e) {
                log.warn("OUT variable not found: {}", x);
                v = null;
            }

            if (v == null) {
                continue;
            }

            result.put(x, v);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getOutExpressions(Variables vars) {
        Object o = vars.getVariable(InternalConstants.Context.OUT_EXPRESSIONS_KEY);
        if (o == null) {
            return null;
        }

        if (!(o instanceof Collection)) {
            throw new IllegalArgumentException("Invalid type of OUT value expression list: " + o.getClass());
        }

        return (Collection<String>) o;
    }
}
