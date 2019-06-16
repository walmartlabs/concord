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

import com.walmartlabs.concord.project.model.ProjectDefinition;
import io.takari.bpm.model.AbstractElement;
import io.takari.bpm.model.ProcessDefinition;
import io.takari.bpm.model.SourceAwareProcessDefinition;
import io.takari.bpm.model.SourceMap;

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
    public List<LintResult> apply(ProjectDefinition pd) {
        notify(">> " + getStartMessage());

        Map<String, ProcessDefinition> flows = pd.getFlows();
        if (flows == null || flows.isEmpty()) {
            return Collections.emptyList();
        }

        List<LintResult> results = new ArrayList<>();
        flows.forEach((key, value) -> results.addAll(apply(value)));

        notify("<< ...done\n");
        return results;
    }

    private List<LintResult> apply(ProcessDefinition pd) {
        List<LintResult> results = new ArrayList<>();

        for (AbstractElement e : pd.getChildren()) {
            if (!accepts(e)) {
                continue;
            }

            SourceMap sm = null;
            if (pd instanceof SourceAwareProcessDefinition) {
                SourceAwareProcessDefinition sapd = (SourceAwareProcessDefinition) pd;
                sm = sapd.getSourceMaps().get(e.getId());
            }

            List<LintResult> r = apply(e, sm);
            if (r != null) {
                results.addAll(r);
            }
        }

        return results;
    }

    protected abstract boolean accepts(AbstractElement element);

    protected abstract List<LintResult> apply(AbstractElement element, SourceMap sourceMap);

    protected abstract String getStartMessage();

    protected void notify(String s) {
        if (!verbose) {
            return;
        }

        System.out.println(s);
    }
}
