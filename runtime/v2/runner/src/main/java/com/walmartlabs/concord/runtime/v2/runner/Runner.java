package com.walmartlabs.concord.runtime.v2.runner;

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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.runner.compiler.CompilerUtils;
import com.walmartlabs.concord.runtime.v2.runner.vm.*;
import com.walmartlabs.concord.runtime.v2.sdk.Compiler;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);

    private final Injector injector;
    private final InstanceId instanceId;
    private final Compiler compiler;
    private final SynchronizationService synchronizationService;
    private final Set<ExecutionListener> listeners;
    private final ProcessStatusCallback statusCallback;

    @Inject
    public Runner(Injector injector,
                  InstanceId instanceId,
                  Compiler compiler,
                  SynchronizationService synchronizationService,
                  Set<ExecutionListener> listeners,
                  ProcessStatusCallback statusCallback) {

        this.injector = injector;
        this.instanceId = instanceId;
        this.compiler = compiler;
        this.synchronizationService = synchronizationService;
        this.listeners = listeners;
        this.statusCallback = statusCallback;
    }

    public ProcessSnapshot start(ProcessConfiguration processConfiguration, ProcessDefinition processDefinition, Map<String, Object> input) throws Exception {
        statusCallback.onRunning(instanceId.getValue());
        log.debug("start ['{}'] -> running...", processConfiguration.entryPoint());

        Command cmd = CompilerUtils.compile(compiler, processConfiguration, processDefinition, processConfiguration.entryPoint());
        State state = new InMemoryState(cmd);
        // install the exception handler into the root frame
        // takes care of all unhandled errors bubbling up
        VMUtils.assertNearestRoot(state, state.getRootThreadId())
                .setExceptionHandler(new BlockCommand(new SaveOutVariablesOnErrorCommand(), new SaveLastErrorCommand()));

        VM vm = createVM(processDefinition);
        // update the global variables using the input map by running a special command
        try {
            vm.run(state, new UpdateLocalsCommand(input)); // TODO merge with the cfg's arguments
        } catch (RuntimeException e) {
            log.error("Error while evaluating process arguments: {}", e.getMessage(), e);
            throw e;
        }
        // start the normal execution
        vm.start(state);

        log.debug("start ['{}'] -> done", processConfiguration.entryPoint());

        return ProcessSnapshot.builder()
                .vmState(state)
                .processDefinition(processDefinition)
                .build();
    }

    public ProcessSnapshot resume(ProcessSnapshot snapshot, Set<String> eventRefs, Map<String, Object> input) throws Exception {
        statusCallback.onRunning(instanceId.getValue());
        log.debug("resume ['{}'] -> running...", eventRefs);

        State state = snapshot.vmState();

        VM vm = createVM(snapshot.processDefinition());

        // update the global variables using the input map by running a special command
        // only the threads with the specified eventRef will receive the input
        Collection<ThreadId> resumingThreads = state.getEventRefs().entrySet().stream()
                .filter(kv -> eventRefs.contains(kv.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        try {
            vm.run(state, new UpdateLocalsCommand(input, resumingThreads));
        } catch (RuntimeException e) {
            log.error("Error while evaluating resume arguments: {}", e.getMessage(), e);
            throw e;
        }

        // resume normally
        vm.resume(state, eventRefs);

        log.debug("resume ['{}'] -> done", eventRefs);

        return ProcessSnapshot.builder()
                .from(snapshot)
                .vmState(state)
                .build();
    }

    public ProcessSnapshot resume(ProcessSnapshot snapshot, Map<String, Object> input) throws Exception {
        statusCallback.onRunning(instanceId.getValue());
        log.debug("resume -> running...");

        State state = snapshot.vmState();

        VM vm = createVM(snapshot.processDefinition());
        // update the global variables using the input map by running a special command
        try {
            vm.run(state, new UpdateLocalsCommand(input));
        } catch (RuntimeException e) {
            log.error("Error while evaluating resume arguments: {}", e.getMessage(), e);
            throw e;
        }

        // continue as usual
        vm.start(state);

        log.debug("resume -> done");

        return ProcessSnapshot.builder()
                .from(snapshot)
                .vmState(state)
                .build();
    }

    private VM createVM(ProcessDefinition processDefinition) {
        Collection<ExecutionListener> listeners = new ArrayList<>();
        listeners.add(new SynchronizationServiceListener(synchronizationService));
        listeners.addAll(this.listeners);

        RuntimeFactory runtimeFactory = vm -> new DefaultRuntime(vm, injectorWithProcessDefinition(injector, processDefinition));

        return new VM(runtimeFactory, listeners);
    }

    private static Injector injectorWithProcessDefinition(Injector injector, ProcessDefinition processDefinition) {
        return injector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(ProcessDefinition.class).toInstance(processDefinition);
            }
        });
    }
}
