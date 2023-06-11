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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Simple in-memory implementation of {@link State}
 */
public class InMemoryState implements Serializable, State {

    private static final long serialVersionUID = 1L;

    private static final Logger log = LoggerFactory.getLogger(InMemoryState.class);

    private final Map<ThreadId, List<Frame>> frames = new HashMap<>();
    private final Map<ThreadId, ThreadStatus> threadStatus = new HashMap<>();
    private final Map<ThreadId, Set<ThreadId>> children = new HashMap<>();
    private final Map<ThreadId, String> eventRefs = new HashMap<>();
    private final Map<ThreadId, Exception> threadErrors = new HashMap<>();
    private final Map<ThreadId, List<StackTraceItem>> stackTrace = new HashMap<>();

    private final ThreadId rootThreadId;

    private long threadIdSeq = 0;

    public InMemoryState(Frame rootFrame) {
        this.rootThreadId = nextThreadId();
        pushFrame(rootThreadId, rootFrame);
    }

    public InMemoryState(Command cmd) {
        this(Frame.builder()
                .root()
                .commands(cmd)
                .build());
    }

    @Override
    public void pushFrame(ThreadId threadId, Frame frame) {
        log.trace("pushFrame {}", threadId);

        synchronized (this) {
            List<Frame> l = frames.computeIfAbsent(threadId, key -> new LinkedList<>());
            l.add(0, frame);
        }
    }

    @Override
    public Frame peekFrame(ThreadId threadId) {
        synchronized (this) {
            List<Frame> l = frames.get(threadId);
            if (l == null || l.isEmpty()) {
                return null;
            }

            return l.get(0);
        }
    }

    @Override
    public void popFrame(ThreadId threadId) {
        log.trace("popFrame {}", threadId);

        synchronized (this) {
            List<Frame> l = frames.get(threadId);
            if (l == null) {
                throw new IllegalStateException("Call frame doesn't exist: " + threadId);
            }

            Frame removed = l.remove(0);
            unwindStackTrace(threadId, removed);
        }
    }

    @Override
    public List<Frame> getFrames(ThreadId threadId) {
        synchronized (this) {
            List<Frame> l = this.frames.get(threadId);
            if (l == null) {
                return Collections.emptyList();
            }

            return Collections.unmodifiableList(new ArrayList<>(l));
        }
    }

    @Override
    public void dropAllFrames() {
        synchronized (this) {
            frames.clear();
        }
    }

    @Override
    public void setStatus(ThreadId threadId, ThreadStatus status) {
        synchronized (this) {
            threadStatus.put(threadId, status);
        }
    }

    @Override
    public ThreadStatus getStatus(ThreadId threadId) {
        synchronized (this) {
            return threadStatus.get(threadId);
        }
    }

    @Override
    public ThreadId getRootThreadId() {
        return rootThreadId;
    }

    @Override
    public void fork(ThreadId parentThreadId, ThreadId threadId, Command... cmds) {
        synchronized (this) {
            setStatus(threadId, ThreadStatus.READY);
            pushFrame(threadId, Frame.builder()
                    .root()
                    .commands(cmds)
                    .build());

            children.computeIfAbsent(parentThreadId, k -> new HashSet<>())
                    .add(threadId);
        }
    }

    @Override
    public Map<ThreadId, ThreadStatus> threadStatus() {
        synchronized (this) {
            return new HashMap<>(threadStatus);
        }
    }

    @Override
    public ThreadId nextThreadId() {
        synchronized (this) {
            long id = threadIdSeq++;
            return new ThreadId(id);
        }
    }

    @Override
    public void setEventRef(ThreadId threadId, String eventRef) {
        // TODO check for uniqueness

        synchronized (this) {
            String old = eventRefs.put(threadId, eventRef);
            if (old != null) {
                throw new IllegalStateException("Thread " + threadId + " already had an unprocessed event ref registered: " + old);
            }
        }
    }

    @Override
    public ThreadId removeEventRef(String eventRef) {
        ThreadId threadId = null;

        synchronized (this) {
            for (Map.Entry<ThreadId, String> e : eventRefs.entrySet()) {
                String s = e.getValue();
                if (eventRef.equals(s)) {
                    threadId = e.getKey();
                    break;
                }
            }

            if (threadId != null) {
                eventRefs.remove(threadId);
            }
        }

        return threadId;
    }

    @Override
    public Map<ThreadId, String> getEventRefs() {
        synchronized (this) {
            return Collections.unmodifiableMap(eventRefs);
        }
    }

    @Override
    public void setThreadError(ThreadId threadId, Exception error) {
        synchronized (this) {
            threadErrors.put(threadId, error);
        }
    }

    @Override
    public Exception clearThreadError(ThreadId threadId) {
        synchronized (this) {
            return threadErrors.remove(threadId);
        }
    }

    @Override
    public List<StackTraceItem> getStackTrace(ThreadId threadId) {
        synchronized (this) {
            // for backward compatibility
            if (stackTrace == null) {
                return Collections.emptyList();
            }
            List<ThreadId> threads = collectParents(threadId);
            threads.add(0, threadId);

            List<StackTraceItem> result = new ArrayList<>();
            threads.forEach(tid -> result.addAll(stackTrace.getOrDefault(tid, Collections.emptyList())));
            return result;
        }
    }

    @Override
    public void pushStackTraceItem(ThreadId threadId, StackTraceItem item) {
        synchronized (this) {
            // for backward compatibility
            if (stackTrace == null) {
                return;
            }

            List<StackTraceItem> l = stackTrace.computeIfAbsent(threadId, key -> new LinkedList<>());
            l.add(0, item);
        }
    }

    @Override
    public void clearStackTrace(ThreadId threadId) {
        synchronized (this) {
            // for backward compatibility
            if (stackTrace == null) {
                return;
            }

            stackTrace.remove(threadId);
        }
    }

    @Override
    public void gc() {
        synchronized (this) {
            Stream<ThreadId> done = threadStatus.entrySet().stream()
                    .filter(e -> e.getValue() == ThreadStatus.DONE)
                    .map(Map.Entry::getKey);

            Stream<ThreadId> handled = threadStatus.entrySet().stream()
                    .filter(e -> e.getValue() == ThreadStatus.FAILED)
                    .filter(e -> !threadErrors.containsKey(e.getKey()))
                    .map(Map.Entry::getKey);

            Stream.concat(done, handled)
                    .collect(Collectors.toList()) // avoid races by eagerly calculating the list of IDs
                    .forEach(k -> {
                        threadErrors.remove(k);
                        threadStatus.remove(k);
                        frames.remove(k);
                        eventRefs.remove(k);
                        children.remove(k);
                        if (stackTrace != null) {
                            stackTrace.remove(k);
                        }
                    });
        }
    }

    private List<ThreadId> collectParents(ThreadId threadId) {
        List<ThreadId> result = new ArrayList<>();
        ThreadId current = threadId;
        while (true) {
            ThreadId parent = findParent(current);
            if (parent != null) {
                result.add(parent);
                current = parent;
            } else {
                break;
            }
        }
        return result;
    }

    private ThreadId findParent(ThreadId threadId) {
        return children.entrySet().stream()
                .filter(e -> e.getValue().contains(threadId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void unwindStackTrace(ThreadId threadId, Frame removed) {
        // for backward compatibility
        if (stackTrace == null || removed.id() == null) {
            return;
        }
        List<StackTraceItem> items = stackTrace.get(threadId);
        if (items == null) {
            return;
        }

        int itemIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            StackTraceItem item = items.get(i);
            if (removed.id().equals(item.getFrameId())) {
                itemIndex = i;
            }
        }

        if (itemIndex >= 0) {
            if (itemIndex + 1 == items.size()) {
                stackTrace.remove(threadId);
            } else {
                stackTrace.put(threadId, new LinkedList<>(items.subList(itemIndex + 1, items.size())));
            }
        }
    }
}
