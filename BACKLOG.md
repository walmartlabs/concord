# Logging Rewrite Backlog

## Objective

Implement the logging rewrite described in [PLAN.md](/mnt/scratch/ibodrov/prj/walmartlabs/concord-agent2/PLAN.md) with these fixed scope decisions:

- segmented mode switches immediately to system segment `0`
- correctness only, no new metrics work
- `logMaxDelay` is preserved as an equivalent flush interval, not exact temp-file polling behavior

## Milestone 1. Lock In Current Behavior

Status:

- complete
- note: a minimal prerequisite production wiring fix was landed separately so worker-stage stdout mirroring could be characterized against the shared appender composition
- separate production commit: `a8f529062` `agent: use shared log appender for worker logs`

### 1.1 Add characterization tests for agent-side logging assembly

Tasks:

- [x] add tests for worker-stage agent messages before runner startup
- [x] add tests for `REDIRECT_PROCESS_LOGS_TO_STDOUT` behavior
- [x] add tests for non-segmented runner output delivery
- [x] add tests for segmented runner output delivery
- [x] add tests for invalid/unframed bytes mapped to system segment `0`
- [x] add tests for segment warning/error counters and final status propagation
- [x] add tests for cancellation and shutdown while output is still being drained

Candidate files:

- `agent/src/test/java/com/walmartlabs/concord/agent/logging/...`
- `agent/src/test/java/com/walmartlabs/concord/agent/executors/runner/...`

Implemented in:

- `agent/src/test/java/com/walmartlabs/concord/agent/WorkerLoggingTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/guice/WorkerModuleTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogFactoryTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessOutputPumpTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoderTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/RecordingLogAppender.java`

Done when:

- all current externally visible behaviors called out in `PLAN.md` are covered by tests
- tests fail if worker-stage stdout mirroring regresses
- tests fail if segment `0` fallback behavior changes

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent test`

## Milestone 2. Introduce New Logging Model

Status:

- complete
- note: the new transport contract now reports explicit delivery status, but production call sites still remain on the legacy `LogAppender` path until later milestones switch them over

### 2.1 Add new core interfaces

Tasks:

- [x] introduce `ProcessLogSession`
- [x] introduce `ProcessLogEvents`
- [x] introduce `ProcessOutputSink`
- [x] introduce `ProcessLogTransport`

Design constraints:

- no unsupported methods
- no mixing of raw subprocess streaming and formatted agent messages in one misleading contract

Done when:

- new interfaces compile alongside the old ones
- no production call sites depend on them yet

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogSession.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogEvents.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessOutputSink.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogTransport.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -DskipTests compile`

### 2.2 Add transport implementations

Tasks:

- [x] add `RemoteProcessLogTransport`
- [x] add `StdoutMirrorTransport`
- [x] add `CompositeProcessLogTransport`

Behavior:

- `RemoteProcessLogTransport` must support:
  - system append
  - segmented append
  - segment update
- stdout mirroring must apply consistently to worker-stage and runner-stage messages

Done when:

- new transport tests cover remote and stdout fan-out behavior
- the transport API is expressive enough to replace `LogAppender`

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/RemoteProcessLogTransport.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/StdoutMirrorTransport.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/CompositeProcessLogTransport.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogTransportTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ProcessLogTransportTest test`

## Milestone 3. Add Direct Output Pumping

Status:

- complete
- note: the new pump and decoders are additive and tested, but production runner/factory call sites still remain on the legacy path until Milestone 5 switches them over

### 3.1 Implement direct subprocess output pump

Tasks:

- [x] add `ProcessOutputPump`
- [x] read subprocess output exactly once
- [x] remove dependency on temp-file reread behavior in the new path
- [x] implement flush behavior bounded by `logMaxDelay`

Done when:

- the new pump can stream subprocess output without a local temp log file
- flush cadence is bounded by `logMaxDelay`

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessOutputPump.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessOutputPumpTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ProcessOutputPumpTest test`

### 3.2 Implement decoder layer

Tasks:

- [x] add `PlainOutputDecoder`
- [x] add `SegmentedOutputDecoder`
- [x] preserve system segment `0` fallback for invalid bytes
- [x] preserve partial-header and partial-payload handling across chunk boundaries

Done when:

- existing parser/consumer tests are either migrated or superseded
- decoder tests cover chunk-boundary edge cases

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessOutputDecoder.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/PlainOutputDecoder.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoder.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/RecordingProcessLogTransport.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/PlainOutputDecoderTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoderTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=PlainOutputDecoderTest,SegmentedOutputDecoderTest test`

## Milestone 4. Wire Agent Messages Into The Same Pipeline

Status:

- complete
- note: worker-stage messages are buffered until runtime segmented mode is known and then flushed through the shared session/transport path
- note: if startup fails before runtime resolution is possible, those buffered worker-stage messages fall back to the non-segmented system-log path because segmented mode cannot be determined yet

### 4.1 Route agent-generated messages through the new session

Tasks:

- [x] ensure `info/warn/error` use the same session and transport stack as streamed subprocess output
- [x] in segmented mode, route agent-generated messages to system segment `0`
- [x] in non-segmented mode, preserve current visible behavior

Done when:

- worker-stage messages and runner-stage messages both respect stdout mirroring
- segmented mode no longer depends on the deprecated whole-log API for agent-generated messages

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/DefaultProcessLogSession.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/SessionProcessLog.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SessionProcessLogTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=SessionProcessLogTest test`

### 4.2 Fix DI composition

Tasks:

- [x] update `WorkerModule` to construct one composite transport
- [x] ensure worker-level process logging uses the same transport composition as runner-level logging
- [x] remove direct instantiation of isolated `RemoteLogAppender` paths where obsolete

Done when:

- there is a single authoritative transport composition per worker
- `REDIRECT_PROCESS_LOGS_TO_STDOUT` applies uniformly

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogModeConfigurator.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ModeAwareProcessLog.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/guice/WorkerModule.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/executors/JobExecutorFactory.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ModeAwareProcessLogTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/guice/WorkerModuleTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ModeAwareProcessLogTest,WorkerModuleTest,DefaultProcessLogSessionTest,SessionProcessLogTest,ProcessLogFactoryTest test`

## Milestone 5. Migrate Executor And Factory Code

Status:

- complete
- note: the active runner path now uses the session-backed process log directly
- note: file-backed redirector classes only remained temporarily as compatibility/test-covered legacy pieces until Milestone 6.1 removed them

### 5.1 Replace runner logging assembly

Tasks:

- [x] update `RunnerJob`
- [x] update `RunnerJobExecutor`
- [x] replace `RunnerLog` usage with the new session/pump model

Done when:

- runner execution no longer requires `RunnerLog`
- subprocess output and agent-generated messages both use the new abstractions

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogFactory.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/executors/runner/RunnerJob.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/executors/runner/RunnerJobExecutor.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogFactoryTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ProcessLogFactoryTest,SegmentedOutputDecoderTest,ProcessOutputPumpTest,JobDependenciesTest test`

### 5.2 Replace process log factory responsibilities

Tasks:

- [x] shrink `ProcessLogFactory` into a small session factory or equivalent builder
- [x] stop creating temp log directories for normal streaming
- [x] keep mode selection based on runtime `segmentedLogs`

Done when:

- factory code is only responsible for session creation and mode selection
- no file-backed redirector remains in the main path

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/ProcessLogFactory.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogFactoryTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessOutputPumpTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoderTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/executors/runner/RunnerJobTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ProcessLogFactoryTest,SegmentedOutputDecoderTest,ProcessOutputPumpTest,RunnerJobTest test`

## Milestone 6. Remove Legacy Logging Pieces

Status:

- complete
- `6.1` complete
- `6.2` complete
- note: the `LogAppender` hierarchy is still the active backend for worker and runner transports, so it is not yet safe to remove

### 6.1 Delete obsolete classes

Expected removals:

- `RunnerLog`
- `RedirectedProcessLog`
- `LocalProcessLog`
- `AbstractProcessLog`
- `SegmentHeaderParser`
- `SegmentedLogsConsumer`
- old `LogAppender` hierarchy, if fully superseded

Done when:

- production code no longer references the removed types
- tests pass without compatibility shims for segmented mode whole-log append

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoder.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoderTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessOutputPumpTest.java`

Removed:

- `agent/src/main/java/com/walmartlabs/concord/agent/executors/runner/RunnerLog.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/AbstractProcessLog.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/LocalProcessLog.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/RedirectedProcessLog.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/SegmentHeaderParser.java`
- `agent/src/main/java/com/walmartlabs/concord/agent/logging/SegmentedLogsConsumer.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/executors/runner/SegmentHeaderParserTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/executors/runner/SegmentedLogsConsumerTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogCharacterizationTest.java`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=SegmentedOutputDecoderTest,ProcessOutputPumpTest,ProcessLogFactoryTest,DefaultProcessLogSessionTest,SessionProcessLogTest,RunnerJobTest,JobDependenciesTest test`

### 6.2 Clean up remaining config/docs/tests

Tasks:

- [x] update code comments to match new behavior
- [x] update tests that referenced temp-file internals
- [x] verify `logMaxDelay` docs describe flush interval semantics accurately

Done when:

- comments and docs no longer describe file polling behavior
- configuration behavior matches documentation

Implemented in:

- `agent/src/main/java/com/walmartlabs/concord/agent/cfg/AgentConfiguration.java`
- `agent/src/main/resources/concord-agent.conf`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessOutputPumpTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/SegmentedOutputDecoderTest.java`

Notes:

- the unused `logDir` configuration wiring was removed from `AgentConfiguration`
- the sample agent config now describes `logMaxDelay` as the direct-stream flush interval
- temp-file-internal characterization tests were removed alongside the obsolete redirector classes in `6.1`
- the old drain-thread shutdown guarantees are now covered on the session/pump path by `ProcessOutputPumpTest`
- partial-header and multi-segment parser behavior is now covered directly on `SegmentedOutputDecoderTest`

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent -Dtest=ProcessOutputPumpTest,SegmentedOutputDecoderTest,ProcessLogFactoryTest,RunnerJobTest,JobDependenciesTest,ModeAwareProcessLogTest,WorkerModuleTest,DefaultProcessLogSessionTest,SessionProcessLogTest test`
- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent test`

## Cross-Cutting Acceptance Criteria

The rewrite is complete only if all of the following hold:

- runtime-v1 non-segmented logging still works
- runtime-v2 segmented logging still works
- worker-stage agent messages are preserved
- worker-stage agent messages mirror to stdout when enabled
- segmented mode uses system segment `0` for agent-generated messages
- `sendSystemOutAndErrToSLF4J=false` behavior remains intact
- `workDirMasking` behavior remains intact
- invalid bytes still fall back to system segment `0`
- partial segments across chunk boundaries are handled correctly
- warning/error counters and final segment status updates are preserved
- shutdown and cancellation do not silently drop already-read log data

## Suggested Execution Order

1. Milestone 1
2. Milestone 2
3. Milestone 3
4. Milestone 4
5. Milestone 5
6. Milestone 6

## Nice-To-Have But Explicitly Out Of Scope

- new delivery metrics
- dashboards or alerting
- broad server-side logging API cleanup outside what is required by the agent rewrite
