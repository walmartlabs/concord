package com.walmartlabs.concord.runtime.v25.runner.task;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class InvocationExecutor implements AutoCloseable {

    private static final ThreadLocal<InvocationExecutor> CURRENT = new ThreadLocal<>();

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore admission;
    private final Duration cancellationGrace;
    private final ThreadLocal<Worker> worker = new ThreadLocal<>();

    public InvocationExecutor(int parallelism, Duration cancellationGrace) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism must be positive");
        }
        if (cancellationGrace == null || cancellationGrace.isNegative() || cancellationGrace.isZero()) {
            throw new IllegalArgumentException("cancellationGrace must be positive");
        }
        this.admission = new Semaphore(parallelism);
        this.cancellationGrace = cancellationGrace;
    }

    public <T> T call(Callable<T> action) {
        var currentWorker = worker.get();
        if (currentWorker != null && currentWorker.admitted) {
            return callInline(action);
        }
        var acquired = false;
        Submitted<T> submitted = null;
        try {
            admission.acquire();
            acquired = true;
            if (currentWorker != null) {
                currentWorker.admitted = true;
                try {
                    return callInline(action);
                } finally {
                    currentWorker.admitted = false;
                }
            }
            submitted = new Submitted<>(executor, worker, this, action);
            return submitted.get();
        } catch (InterruptedException e) {
            var terminated = submitted == null || submitted.cancelAndAwait(cancellationGrace);
            Thread.currentThread().interrupt();
            if (!terminated) {
                throw new ShutdownException(cancellationGrace);
            }
            var cancelled = new CancellationException("Invocation interrupted");
            cancelled.initCause(e);
            throw cancelled;
        } catch (ExecutionException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } finally {
            if (acquired) {
                admission.release();
            }
        }
    }

    private static <T> T callInline(Callable<T> action) {
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Error e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T withCurrent(InvocationExecutor executor, Callable<T> action) {
        var previous = CURRENT.get();
        CURRENT.set(executor);
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static <T> T callCurrent(Callable<T> action) {
        var current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException("No process invocation executor is active");
        }
        return current.call(action);
    }
    public static <T> Future<T> submitCurrent(Callable<T> action) {
        var current = CURRENT.get();
        if (current == null) {
            throw new IllegalStateException("No process invocation executor is active");
        }
        var submittingWorker = current.worker.get();
        var transferred = submittingWorker != null && submittingWorker.admitted;
        if (transferred) {
            submittingWorker.admitted = false;
        }
        var future = new AdmittedFuture<T>(() -> withCurrent(current, () -> {
            var acquired = false;
            try {
                if (!transferred) {
                    current.admission.acquire();
                    acquired = true;
                }
                current.worker.set(new Worker());
                return action.call();
            } finally {
                current.worker.remove();
                if (transferred || acquired) {
                    current.admission.release();
                }
            }
        }), transferred, submittingWorker);
        try {
            current.executor.execute(future);
            return future;
        } catch (RuntimeException e) {
            future.cancel(false);
            throw e;
        }
    }

    public static final class ShutdownException extends RuntimeException {

        private ShutdownException(Duration grace) {
            super("Invocation did not terminate within cancellation grace " + grace);
        }
    }

    private static final class Submitted<T> {

        private final CountDownLatch finished = new CountDownLatch(1);

        private Future<T> future;
        private boolean started;
        private boolean cancelled;

        private Submitted(ExecutorService executor, ThreadLocal<Worker> worker, InvocationExecutor invocationExecutor,
                          Callable<T> action) {
            synchronized (this) {
                future = executor.submit(() -> {
                    if (!begin()) {
                        return null;
                    }
                    worker.set(new Worker());
                    var previous = CURRENT.get();
                    CURRENT.set(invocationExecutor);
                    try {
                        return action.call();
                    } finally {
                        if (previous == null) {
                            CURRENT.remove();
                        } else {
                            CURRENT.set(previous);
                        }
                        worker.remove();
                        finished.countDown();
                    }
                });
            }
        }

        private T get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        private synchronized boolean begin() {
            if (cancelled) {
                finished.countDown();
                return false;
            }
            started = true;
            return true;
        }

        private boolean cancelAndAwait(Duration grace) {
            synchronized (this) {
                cancelled = true;
                future.cancel(true);
                if (!started) {
                    finished.countDown();
                }
            }
            var deadline = System.nanoTime() + grace.toNanos();
            while (true) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    return finished.await(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static final class AdmittedFuture<T> extends FutureTask<T> {

        private final boolean transferred;
        private final Worker submittingWorker;
        private boolean started;

        private AdmittedFuture(Callable<T> action, boolean transferred, Worker submittingWorker) {
            super(action);
            this.transferred = transferred;
            this.submittingWorker = submittingWorker;
        }

        @Override
        public void run() {
            synchronized (this) {
                if (isCancelled()) {
                    return;
                }
                started = true;
            }
            super.run();
        }

        @Override
        protected void done() {
            if (isCancelled()) {
                synchronized (this) {
                    if (!started && transferred) {
                        submittingWorker.admitted = true;
                    }
                }
            }
        }
    }

    private static final class Worker {

        private boolean admitted = true;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
