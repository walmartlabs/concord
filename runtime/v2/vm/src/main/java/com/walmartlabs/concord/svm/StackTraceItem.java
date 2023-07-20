package com.walmartlabs.concord.svm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import java.io.Serializable;

public class StackTraceItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private final FrameId frameId;

    private final ThreadId threadId;

    private final String fileName;

    private final String flowName;

    private final int lineNumber;

    private final int columnNumber;

    public StackTraceItem(FrameId frameId, ThreadId threadId, String fileName, String flowName, int lineNumber, int columnNumber) {
        this.frameId = frameId;
        this.threadId = threadId;
        this.fileName = fileName;
        this.flowName = flowName;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
    }

    public FrameId getFrameId() {
        return frameId;
    }

    public ThreadId getThreadId() {
        return threadId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFlowName() {
        return flowName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    @Override
    public String toString() {
        return String.format("(%s) @ line: %d, col: %d, thread: %s, flow: %s",
                nullToNa(fileName), lineNumber, columnNumber, threadId.id(), flowName);
    }

    private static String nullToNa(String value) {
        if (value == null) {
            return "n/a";
        }
        return value;
    }

}
