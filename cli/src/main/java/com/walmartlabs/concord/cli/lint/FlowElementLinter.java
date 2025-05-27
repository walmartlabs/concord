package com.walmartlabs.concord.cli.lint;

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

import com.walmartlabs.concord.runtime.model.FlowDefinition;
import com.walmartlabs.concord.runtime.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.model.Step;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class FlowElementLinter implements Linter {

    private final boolean verbose;

    public FlowElementLinter(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<LintResult> apply(ProcessDefinition pd) {
        notify(">> " + getStartMessage());

        Map<String, FlowDefinition> flows = pd.flows();
        if (flows == null || flows.isEmpty()) {
            return Collections.emptyList();
        }

        List<LintResult> results = new ArrayList<>();
        flows.forEach((key, value) -> results.addAll(apply(value)));

        notify("<< ...done\n");
        return results;
    }

    private List<LintResult> apply(FlowDefinition pd) {
        List<LintResult> results = new ArrayList<>();

        for (Step e : pd.steps()) {
            if (!accepts(e)) {
                continue;
            }

            List<LintResult> r = apply(e);
            if (r != null) {
                results.addAll(r);
            }
        }

        return results;
    }

    protected abstract boolean accepts(Step element);

    protected abstract List<LintResult> apply(Step element);

    protected abstract String getStartMessage();

    protected void notify(String s) {
        if (!verbose) {
            return;
        }

        System.out.println(s);
    }
}
