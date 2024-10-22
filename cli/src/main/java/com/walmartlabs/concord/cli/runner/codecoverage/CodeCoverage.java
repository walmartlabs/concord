package com.walmartlabs.concord.cli.runner.codecoverage;

/*-
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

import com.walmartlabs.concord.cli.runner.CliProcessEventsWriter;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.svm.ExecutionListener;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import java.nio.file.Path;

public class CodeCoverage implements ExecutionListener {

    private final LcovReportProducer reportProducer;
    private final Path workingDirectory;

    @Inject
    public CodeCoverage(WorkingDirectory workingDirectory) {
        this.reportProducer = new LcovReportProducer();
        this.workingDirectory = workingDirectory.getValue();
    }

    @Override
    public void beforeProcessStart(Runtime runtime, State state) {
        ProcessDefinition processDefinition = runtime.getService(ProcessDefinition.class);
        reportProducer.init(processDefinition);
    }

    @Override
    public void onProcessError(Runtime runtime, State state, Exception e) {
        generateReport(runtime);
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        generateReport(runtime);
    }

    private void generateReport(Runtime runtime) {
        System.out.println("===");
        System.out.println("Generating code coverage info...");

        CliProcessEventsWriter eventsHolder = runtime.getService(CliProcessEventsWriter.class);

        reportProducer.onEvents(eventsHolder.events());

        try {
            Path coverageInfo = reportProducer.produce(workingDirectory);
            System.out.println("Coverage info file: " + coverageInfo);
            System.out.println("To generate HTML report with lcov use:");
            System.out.println("\tcd " + workingDirectory + " && genhtml coverage.info --output-directory out");
            System.out.println("===");
        } catch (Exception e) {
            throw new RuntimeException("Can't generate code coverage report", e);
        }
    }
}
