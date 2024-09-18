package com.walmartlabs.concord.runtime.v2.runner;

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

import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Serializable;
import java.util.*;

@Singleton
public class TaskResultService implements ExecutionListener {

    private HashMap<String, List<Serializable>> taskResults = new HashMap<>();
    private final PersistenceService persistenceService;

    private final Object mutex = new Object();

    @Inject
    public TaskResultService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void store(String taskName, Serializable result) {
        synchronized (mutex) {
            List<Serializable> results = taskResults.computeIfAbsent(taskName, s -> new ArrayList<>());
            results.add(result);
        }
    }

    public Map<String, List<Serializable>> getResults() {
        synchronized (mutex) {
            return Collections.unmodifiableMap(taskResults);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeProcessStart(Runtime runtime, State state) {
        taskResults = persistenceService.load("taskResults", HashMap.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void beforeProcessResume(Runtime runtime, State state) {
        taskResults = persistenceService.load("taskResults", HashMap.class);
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        boolean isSuspended = state.threadStatus().entrySet().stream()
                .anyMatch(e -> e.getValue() == ThreadStatus.SUSPENDED);
        if (!isSuspended) {
            return;
        }

        try {
            persistenceService.save("taskResults", taskResults);
        } catch (Exception e) {
            throw new RuntimeException("Task results save error", e);
        }
    }
}
