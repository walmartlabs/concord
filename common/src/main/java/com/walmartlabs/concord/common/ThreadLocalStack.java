package com.walmartlabs.concord.common;

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

import java.util.LinkedList;
import java.util.List;

public class ThreadLocalStack<T> {

    private final ThreadLocal<List<T>> localStack = ThreadLocal.withInitial(LinkedList::new);

    public void push(T value) {
        List<T> stack = localStack.get();
        if (stack == null) {
            stack = new LinkedList<>();
            localStack.set(stack);
        }
        stack.add(0, value);
    }

    public T pop() {
        List<T> stack = localStack.get();
        if (stack == null || stack.isEmpty()) {
            throw new IllegalStateException("Stack is empty. This is most likely a bug.");
        }

        T result = stack.remove(0);
        if (stack.isEmpty()) {
            localStack.remove();
        }
        return result;
    }

    public T peek() {
        List<T> stack = localStack.get();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.get(0);
    }
}
