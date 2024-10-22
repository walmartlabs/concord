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

import com.walmartlabs.concord.client2.ProcessEventEntry;
import com.walmartlabs.concord.runtime.v2.model.Flow;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LcovReportProducer {

    private static final Logger log = LoggerFactory.getLogger(LcovReportProducer.class);

    private final Map<String, Stats> statsPerFile = new HashMap<>();

    public void init(ProcessDefinition processDefinition) {
        for (Map.Entry<String, Flow> fd : processDefinition.flows().entrySet()) {
            String fileName = fd.getValue().location().fileName();

            Stats stats = statsPerFile.computeIfAbsent(fileName, k -> new Stats());
            stats.init(fd.getKey(), fd.getValue().location().lineNum());
        }
    }

    public void onEvents(List<ProcessEventEntry> events) {
        for (ProcessEventEntry event : events) {
            if ("post".equals(ProcessEventData.phase(event))) {
                continue;
            }

            String fileName = ProcessEventData.fileName(event);
            Integer lineNum = ProcessEventData.line(event);
            if (lineNum == null) {
                continue;
            }

            Stats stats = statsPerFile.get(fileName);
            if (stats == null) {
                log.warn("Can't find definitions for {}. This is most likely a bug.", fileName);
                continue;
            }

            stats.onLineExecuted(lineNum);

            String flowCallName = ProcessEventData.flowCallName(event);
            if (flowCallName != null) {
                Stats statsForFlow = findStatsForFlow(flowCallName);
                if (statsForFlow != null) {
                    statsForFlow.onFlowCall(flowCallName);
                }
            }

            String processDefinitionId = ProcessEventData.processDefinitionId(event);
            if (processDefinitionId != null) {
                // as we do not have call step for entry point
                Stats statsForFlow = findStatsForFlow(processDefinitionId);
                if (statsForFlow != null) {
                    statsForFlow.markCalled(processDefinitionId);
                }
            }
        }
    }

    public Path produce(Path baseDir) throws IOException {
        Path reportName = baseDir.resolve("coverage.info");

        try (BufferedWriter writer = Files.newBufferedWriter(reportName, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Map.Entry<String, Stats> statsEntry : statsPerFile.entrySet()) {
                // TN (Test Name):
                // Optional. Can be left empty or used to specify the name of the test.
                writer.write("TN:");
                writer.newLine();

                // SF (Source File Path):
                // Specifies the path to the source file for which the coverage data is provided.
                writer.write("SF:" + statsEntry.getKey());
                writer.newLine();

                Stats stats = statsEntry.getValue();
                for (Map.Entry<String, Integer> flowEntry : stats.flowLocationByName.entrySet()) {
                    // FN (Function):
                    // The start line and name of the function in the source file.
                    writer.write(String.format("FN:%d,%s", flowEntry.getValue(), flowEntry.getKey()));
                    writer.newLine();
                }

                for (Map.Entry<String, Integer> execEntry : stats.flowExecCountByName.entrySet()) {
                    // FNDA (Function Data):
                    // The number of times the function was executed, followed by the function name.
                    writer.write(String.format("FNDA:%d,%s", execEntry.getValue(), execEntry.getKey()));
                    writer.newLine();
                }

                // Function summary
                // FNF (Functions Found):
                // The number of functions found in the file.
                writer.write("FNF:" + stats.flowLocationByName.size());
                writer.newLine();

                // FNH (Functions Hit):
                // The number of functions that were executed at least once.
                writer.write("FNH:" + stats.flowExecCountByName.size());
                writer.newLine();

                // Line coverage data
                // DA (Data Array):
                // Line number and the execution count for that line.
                for (Map.Entry<Integer, Integer> e : stats.stepExecCountByLineNumber.entrySet()) {
                    writer.write(String.format("DA:%d,%d", e.getKey(), e.getValue()));
                    writer.newLine();
                }

                // Branch coverage data
                // BRDA (Branch Data Array):
                // Line number, block number, branch number, and the execution count for branches.
                //
                // Branch summary
                // BRF (Branches Found):
                // The total number of branches found.
                //
                // BRH (Branches Hit):
                // The number of branches that were taken.

                // line summary
                // LF (Lines Found):
                // The total number of lines found in the source file.
                writer.write("LF:7");
                writer.newLine();
                // LH (Lines Hit):
                // The number of lines that were executed at least once.
                writer.write("LH:6");
                writer.newLine();

                // End of record
                writer.write("end_of_record");
                writer.newLine();
            }
        }

        return reportName;
    }

    private Stats findStatsForFlow(String flow) {
        for (Map.Entry<String, Stats> stats : statsPerFile.entrySet()) {
            if (stats.getValue().containsFlow(flow)) {
                return stats.getValue();
            }
        }
        log.warn("Can't find stats for {} flow. This is most likely a bug.", flow);
        return null;
    }

    private static class Stats {

        private final Map<String, Integer> flowLocationByName = new HashMap<>();

        private final Map<String, Integer> flowExecCountByName = new HashMap<>();

        private final Map<Integer, Integer> stepExecCountByLineNumber = new HashMap<>();

        public void init(String flowName, int location) {
            flowLocationByName.put(flowName, location);
            stepExecCountByLineNumber.put(location, 0);
        }

        public void onLineExecuted(int lineNum) {
            stepExecCountByLineNumber.compute(lineNum, (k, v) -> v == null ? 1 : v + 1);
        }

        public void onFlowCall(String flow) {
            assertContainsFlow(flow);

            flowExecCountByName.compute(flow, (k, v) -> v == null ? 1 : v + 1);
            onLineExecuted(flowLocationByName.get(flow));
        }

        public void markCalled(String flow) {
            assertContainsFlow(flow);

            Integer prev = flowExecCountByName.putIfAbsent(flow, 1);
            if (prev == null) {
                onLineExecuted(flowLocationByName.get(flow));
            }
        }

        public boolean containsFlow(String flow) {
            return flowLocationByName.containsKey(flow);
        }

        private void assertContainsFlow(String flow) {
            if (!flowLocationByName.containsKey(flow)) {
                throw new IllegalArgumentException("Trying update stats for flow without flow definition " + flow);
            }
        }
    }
}
