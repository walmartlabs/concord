package com.walmartlabs.concord.sdk;

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

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Process execution context.
 */
public interface Context {

    /**
     * Returns a process variable {@code key} or {@code null}, if no such variable exists.
     *
     * @param key
     * @return
     */
    Object getVariable(String key);

    /**
     * Sets the value of the specified variable.
     *
     * @param key
     * @param value
     */
    void setVariable(String key, Object value);

    /**
     * Removes the specified variable if it exists.
     *
     * @param key
     */
    void removeVariable(String key);

    /**
     * Sets a "protected" variable. Such variables can be set only by the tasks that are
     * listed in "protectedTask" category in the process' policy.
     *
     * @param key
     * @param value
     */
    void setProtectedVariable(String key, Object value);

    /**
     * Returns the value of a "protected" variable.
     *
     * @param key
     * @return
     * @see #setProtectedVariable(String, Object)
     */
    Object getProtectedVariable(String key);

    /**
     * Returns names of all existing variables of the process.
     *
     * @return
     */
    Set<String> getVariableNames();

    /**
     * Evaluates the expression, returning the results of the specified type.
     *
     * @param expr
     * @param type
     * @param <T>
     * @return
     */
    <T> T eval(String expr, Class<T> type);

    /**
     * "Interpolates" the specified value, resolving all variables.
     * All expression strings will be evaluated and replaced with resulting values.
     *
     * @param v
     * @return
     */
    Object interpolate(Object v);

    /**
     * "Interpolates" the specified value, using the specified map's keys as
     * variables. All expression strings will be evaluated and replaced with
     * resulting values.
     *
     * @param v
     * @param variables
     * @return
     */
    Object interpolate(Object v, Map<String, Object> variables);

    /**
     * Returns process variables as a {@code Map}.
     *
     * @return
     */
    Map<String, Object> toMap();

    /**
     * Requests process suspension. After the task is finished, the process will be suspended and can be resumed
     * with the specified {@code eventName}.
     * <p>
     * Calling {@code suspend(null)} will "undo" the process suspension request.
     *
     * @param eventName name of the event which can be used to resume the process. Should be unique for the process.
     */
    void suspend(String eventName);

    /**
     * Reserved for future use.
     *
     * @see #suspend(String)
     */
    void suspend(String eventName, Object payload, boolean resumeFromSameStep);

    /**
     * Creates a new form and suspends the process.
     *
     * @param formName    the form's name
     * @param formOptions the form's options. The data structure is the same as the form call's syntax.
     */
    void form(String formName, Map<String, Object> formOptions);

    /**
     * Returns the ID of a current process definition (flow).
     *
     * @return
     */
    String getProcessDefinitionId();

    /**
     * Returns the ID of a current process element (flow step).
     *
     * @return
     */
    String getElementId();

    /**
     * Returns the ID of the parent process event. Can be {@code null}.
     *
     * @return
     */
    default UUID getEventCorrelationId() {
        throw new IllegalStateException("Not supported");
    }

    /**
     * Return current flow name.
     */
    String getCurrentFlowName();
}
