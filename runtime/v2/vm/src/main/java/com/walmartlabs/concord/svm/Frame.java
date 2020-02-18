package com.walmartlabs.concord.svm;

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Frame or "call frame" represents a scope with a list of commands, local
 * variables and an optional exception handling command.
 */
public class Frame implements Serializable {

    /**
     * The last handled exception is stored as a local frame variable using this key.
     */
    public static final String LAST_EXCEPTION_KEY = "__lastException";

    private static final long serialVersionUID = 1L;

    private final List<Command> commandStack = new LinkedList<>();
    private final Map<String, Serializable> locals = new HashMap<>();

    private Command exceptionHandler;

    public Frame() {
    }

    public Frame(Command cmd, Command exceptionHandler) {
        push(cmd);
        this.exceptionHandler = exceptionHandler;
    }

    public Frame(Command cmd) {
        this(cmd, null);
    }

    public void push(Command cmd) {
        commandStack.add(0, cmd);
    }

    public Command peek() {
        if (commandStack.isEmpty()) {
            return null;
        }

        return commandStack.get(0);
    }

    public void pop() {
        commandStack.remove(0);
    }

    public Command getExceptionHandler() {
        return exceptionHandler;
    }

    public void setExceptionHandler(Command exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public void clearExceptionHandler() {
        this.exceptionHandler = null;
    }

    public void setLocal(String k, Serializable v) {
        locals.put(k, v);
    }

    public Serializable getLocal(String k) {
        return locals.get(k);
    }
}
