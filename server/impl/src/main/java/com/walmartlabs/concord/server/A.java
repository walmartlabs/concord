package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.sdk.audit.AuditLogListener;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class A {

    private static final Logger log = LoggerFactory.getLogger(A.class);

    private static final int SEND_TIME = 500;

    private static final Duration MAX_LISTENER_TIME = Duration.ofSeconds(3);
    private static final int MAX_LISTENER_THREADS = 5;

    private final ForkJoinPool auditLogListenerPool;
    private final Collection<AuditLogListener> auditLogListeners;

    private static void waitFor(ForkJoinTask<?> task) {
        try {
            task.get(MAX_LISTENER_TIME.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.info("-->" + Listener.producerDroppedMessages.get());
            throw new RuntimeException(e);
        }
    }

    public void onAuditEvent(AuditEvent event) {
        ForkJoinTask<?> task = auditLogListenerPool.submit(() -> {
            auditLogListeners.parallelStream().forEach(l -> l.onEvent(event));
        });

        waitFor(task);
    }

    public A(Collection<AuditLogListener> auditLogListeners) {
        this.auditLogListenerPool = new ForkJoinPool(MAX_LISTENER_THREADS);
        this.auditLogListeners = auditLogListeners;
    }

    static class Listener implements AuditLogListener {

        private static class Record {
            private final long i;

            public Record(long i) {
                this.i = i;
            }
        }

        private final BlockingQueue<Record> messageQueue;
        private final int queueWaitTime;

        public static final AtomicLong producerQueuedMessages = new AtomicLong(0);
        public static final AtomicLong producerDroppedMessages = new AtomicLong(0);
        public static final AtomicLong producerErroredMessages = new AtomicLong(0);

        public Listener(int queueSize, int queueWaitTime) {
            this.messageQueue = new LinkedBlockingQueue<>(queueSize);
            this.queueWaitTime = queueWaitTime;

            new Thread(new MessageForwarder()).start();
        }

        @Override
        public void onEvent(AuditEvent event) {
            try {
                Record producerRecord = new Record(event.entrySeq());
                long start = System.currentTimeMillis();
                boolean offered = this.messageQueue.offer(producerRecord,
                        queueWaitTime,
                        TimeUnit.MILLISECONDS);
                long end = System.currentTimeMillis();

                if (end - start > 1000) {
                    log.info(">>>" + (end -start));
                }

                if (offered) {
                    producerQueuedMessages.incrementAndGet();
                } else {
                    producerDroppedMessages.incrementAndGet();
                }
            } catch (Exception e) {
                producerErroredMessages.incrementAndGet();
                log.error("send -> error", e);
            }
        }

        class MessageForwarder implements Runnable {

            public void run () {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Record r = messageQueue.take();
//                        producerQueuedMessages.decrementAndGet();

                        Thread.sleep(SEND_TIME);
                        log.info(">>>" + r.i);
                    }
                } catch (InterruptedException e) {
                    log.info("Interrupting message sender thread");
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public static class Producer implements Runnable {

        private static final AtomicLong counter = new AtomicLong(0);

        private final A a;

        public Producer(A a) {
            this.a = a;
        }

        @Override
        public void run() {
            for (int i = 0; i < 100; i++) {
                a.onAuditEvent(AuditEvent.builder()
                        .entrySeq(counter.incrementAndGet())
                        .entryDate(OffsetDateTime.now())
                        .object("object")
                        .action("action")
                        .details(Collections.emptyMap())
                        .build());
            }
            log.info("Q-->" + Listener.producerQueuedMessages.get());
            log.info("DROP-->" + Listener.producerDroppedMessages.get());
        }
    }

    public static void main(String[] args) throws Exception {
        int queueSize = 1;
        int queueWaitTime = 1000;

        A a = new A(Collections.singletonList(new Listener(queueSize, queueWaitTime)));

        for (int i = 0; i < 10; i++) {
            new Thread(new Producer(a)).start();
        }

        Thread.sleep(1000_000);
    }
}
