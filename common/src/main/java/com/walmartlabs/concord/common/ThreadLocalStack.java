package com.walmartlabs.concord.common;

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
