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

import com.walmartlabs.concord.runtime.model.ProcessDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Linter that checks for duplicate entries in the dependencies list.
 * Reports warnings when the same dependency string appears more than once
 * in configuration.dependencies.
 */
public class DuplicateDependencyLinter implements Linter {

    private final boolean verbose;

    public DuplicateDependencyLinter(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<LintResult> apply(ProcessDefinition pd) {
        List<LintResult> results = new ArrayList<>();

        if (verbose) {
            System.out.println("Checking for duplicate dependencies...");
        }

        List<String> dependencies = pd.configuration().dependencies();
        if (dependencies == null || dependencies.isEmpty()) {
            return results;
        }

        Set<String> seen = new HashSet<>();
        for (String dep : dependencies) {
            if (!seen.add(dep)) {
                results.add(new LintResult(
                        LintResult.Type.WARNING,
                        null,
                        "Duplicate dependency found: " + dep));

                if (verbose) {
                    System.out.println("  WARN: Duplicate dependency: " + dep);
                }
            }
        }

        if (verbose && results.isEmpty()) {
            System.out.println("  No duplicate dependencies found.");
        }

        return results;
    }
}
