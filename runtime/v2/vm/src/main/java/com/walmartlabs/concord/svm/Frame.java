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
import java.util.*;

/**
 * Frame or "call frame" represents a scope with a list of commands, local
 * variables and an optional exception handling command.
 */
public class Frame implements Serializable {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The last handled exception is stored as a local frame variable using this key.
     */
    public static final String LAST_EXCEPTION_KEY = "__lastException";

    private static final long serialVersionUID = 1L;

    private final FrameId id;
    private final FrameType type;
    private final List<Command> commandStack;
    private final Map<String, Serializable> locals;

    private Command exceptionHandler;

    private Frame(Builder b) {
        this.id = new FrameId(UUID.randomUUID());
        this.type = b.type;

        this.commandStack = new LinkedList<>();
        if (b.commands != null) {
            for (Command cmd : b.commands) {
                push(cmd);
            }
        }

        this.locals = Collections.synchronizedMap(new LinkedHashMap<>(b.locals != null ? b.locals : Collections.emptyMap()));

        this.exceptionHandler = b.exceptionHandler;
    }

    public FrameId id() {
        return id;
    }

    public List<Command> getCommands() {
        List<Command> result = new ArrayList<>(commandStack);
        Collections.reverse(result);
        return result;
    }

    public Command peek() {
        if (commandStack.isEmpty()) {
            return null;
        }

        return commandStack.get(0);
    }

    public FrameType getType() {
        return type;
    }

    public void push(Command cmd) {
        commandStack.add(0, cmd);
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

    public boolean hasLocal(String k) {
        return locals.containsKey(k);
    }

    public void setLocal(String k, Serializable v) {
        locals.put(k, v);
    }

    public Serializable getLocal(String k) {
        return locals.get(k);
    }

    public Map<String, Serializable> getLocals() {
        return Collections.unmodifiableMap(locals);
    }

    public static class Builder {

        private FrameType type = FrameType.ROOT;
        private Command exceptionHandler;
        private List<Command> commands;
        private Map<String, Serializable> locals;

        private Builder() {
        }

        public Builder root() {
            this.type = FrameType.ROOT;
            return this;
        }

        public Builder nonRoot() {
            this.type = FrameType.NON_ROOT;
            return this;
        }

        public Builder exceptionHandler(Command exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public Builder locals(Map<String, Object> locals) {
            if (locals == null || locals.isEmpty()) {
                return this;
            }

            if (this.locals == null) {
                this.locals = new LinkedHashMap<>(); // preserve order of the keys
            }

            locals.forEach((k, v) -> {
                if (v == null || v instanceof Serializable) {
                    this.locals.put(k, (Serializable) v);
                } else {
                    throw new IllegalStateException("Can't set a non-serializable local variable: " + k + " -> " + v.getClass());
                }
            });

            return this;
        }

        /**
         * Add one or more commands to the frame's stack. Commands will be pushed
         * to the stack in the original order which means that the first command
         * in the {@code cmds} array will be executed last.
         */
        public Builder commands(Command... cmds) {
            if (cmds == null || cmds.length == 0) {
                return this;
            }

            if (this.commands == null) {
                this.commands = new ArrayList<>();
            }

            this.commands.addAll(Arrays.asList(cmds));

            return this;
        }

        public Frame build() {
            return new Frame(this);
        }
    }
}
