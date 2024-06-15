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
import java.util.List;
import java.util.Map;

/**
 * Describes the state of the VM.
 */
public interface State extends Serializable {

    /**
     * Adds a frame to the specified thread. The added frame becomes the current frame of the thread.
     */
    void pushFrame(ThreadId threadId, Frame frame);

    /**
     * Returns the current frame of the specified thread. Or {@code null} if there are no frames left.
     */
    Frame peekFrame(ThreadId threadId);

    /**
     * Removes the current frame of the specified thread. The next frame becomes the current frame
     * of the thread.
     * @apiNote Prefer using {@link PopFrameCommand} instead of calling this method
     * directly -- the command supports proper "finally" semantics.
     */
    void popFrame(ThreadId threadId);

    /**
     * Returns an unmodifiable list of frames for the specified thread.
     * The most recent frame is always the first element in the list.
     */
    List<Frame> getFrames(ThreadId threadId);

    /**
     * Removes all frames in all threads effectively stopping the execution.
     */
    void dropAllFrames();

    /**
     * Sets the execution status of the specified thread.
     */
    void setStatus(ThreadId threadId, ThreadStatus status);

    /**
     * Returns the execution status of the specified thread.
     */
    ThreadStatus getStatus(ThreadId threadId);

    /**
     * Returns the root thread ID of the current {@link State} instance.
     */
    ThreadId getRootThreadId();

    /**
     * Creates new "logical" thread using the provided parent thread ID and an initial command.
     */
    void fork(ThreadId parentThreadId, ThreadId threadId, Command... cmds);

    /**
     * Returns a snapshot of the current thread statuses.
     */
    Map<ThreadId, ThreadStatus> threadStatus();

    /**
     * Returns a next thread ID. Unique per {@link State} instance.
     */
    ThreadId nextThreadId();

    /**
     * Adds a new event reference to the specified thread. If the thread suspends
     * those event references can be used to resume it.
     * The event reference must be unique across all threads in the current {@link State}.
     */
    void setEventRef(ThreadId threadId, String eventRef);

    /**
     * Removes the specified event reference and return the ID of a thread that was
     * associated with the event. Returns {@code null} if no threads had the specified event.
     */
    ThreadId removeEventRef(String eventRef);

    /**
     * Returns a map of thread IDs and event references associated with those threads.
     */
    Map<ThreadId, String> getEventRefs();

    /**
     * @return the error for the specified thread. Returns {@code null} if no error was set.
     */
    Exception getThreadError(ThreadId threadId);

    /**
     * Sets an error for the specified thread. Those errors are used to propagate unhandled
     * exceptions from child threads to the parent thread with a {@link com.walmartlabs.concord.svm.commands.Join}.
     */
    void setThreadError(ThreadId threadId, Exception error);

    /**
     * Clears and returns the thread's error. Null if no error was set.
     */
    Exception clearThreadError(ThreadId threadId);

    /**
     * Returns call stack trace for thread id.
     */
    List<StackTraceItem> getStackTrace(ThreadId threadId);

    /**
     * Adds a stack trace item to the specified thread.
     */
    void pushStackTraceItem(ThreadId threadId, StackTraceItem item);

    /**
     * Clears stack trace for the specific thread.
     */
    void clearStackTrace(ThreadId threadId);

    /**
     * Sets a thread-local variable for the specified thread.
     */
    void setThreadLocal(ThreadId threadId, String key, Serializable value);

    /**
     * Retrieves the value of a thread-local variable for the specified thread.
     */
    <T extends Serializable> T getThreadLocal(ThreadId threadId, String key);

    /**
     * Removes a thread-local variable for the specified thread.
     */
    void removeThreadLocal(ThreadId threadId, String key);

    /**
     * Performs state maintenance and cleanup.
     */
    void gc();
}
