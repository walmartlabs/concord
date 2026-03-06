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
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/ProcessLogCharacterizationTest.java`
- `agent/src/test/java/com/walmartlabs/concord/agent/logging/RecordingLogAppender.java`

Done when:

- all current externally visible behaviors called out in `PLAN.md` are covered by tests
- tests fail if worker-stage stdout mirroring regresses
- tests fail if segment `0` fallback behavior changes

Verification:

- `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent test`

## Milestone 2. Introduce New Logging Model

### 2.1 Add new core interfaces

Tasks:

- introduce `ProcessLogSession`
- introduce `ProcessLogEvents`
- introduce `ProcessOutputSink`
- introduce `ProcessLogTransport`

Design constraints:

- no unsupported methods
- no mixing of raw subprocess streaming and formatted agent messages in one misleading contract

Done when:

- new interfaces compile alongside the old ones
- no production call sites depend on them yet

### 2.2 Add transport implementations

Tasks:

- add `RemoteProcessLogTransport`
- add `StdoutMirrorTransport`
- add `CompositeProcessLogTransport`

Behavior:

- `RemoteProcessLogTransport` must support:
  - system append
  - segmented append
  - segment update
- stdout mirroring must apply consistently to worker-stage and runner-stage messages

Done when:

- new transport tests cover remote and stdout fan-out behavior
- the transport API is expressive enough to replace `LogAppender`

## Milestone 3. Add Direct Output Pumping

### 3.1 Implement direct subprocess output pump

Tasks:

- add `ProcessOutputPump`
- read subprocess output exactly once
- remove dependency on temp-file reread behavior in the new path
- implement flush behavior bounded by `logMaxDelay`

Done when:

- the new pump can stream subprocess output without a local temp log file
- flush cadence is bounded by `logMaxDelay`

### 3.2 Implement decoder layer

Tasks:

- add `PlainOutputDecoder`
- add `SegmentedOutputDecoder`
- preserve system segment `0` fallback for invalid bytes
- preserve partial-header and partial-payload handling across chunk boundaries

Done when:

- existing parser/consumer tests are either migrated or superseded
- decoder tests cover chunk-boundary edge cases

## Milestone 4. Wire Agent Messages Into The Same Pipeline

### 4.1 Route agent-generated messages through the new session

Tasks:

- ensure `info/warn/error` use the same session and transport stack as streamed subprocess output
- in segmented mode, route agent-generated messages to system segment `0`
- in non-segmented mode, preserve current visible behavior

Done when:

- worker-stage messages and runner-stage messages both respect stdout mirroring
- segmented mode no longer depends on the deprecated whole-log API for agent-generated messages

### 4.2 Fix DI composition

Tasks:

- update `WorkerModule` to construct one composite transport
- ensure worker-level process logging uses the same transport composition as runner-level logging
- remove direct instantiation of isolated `RemoteLogAppender` paths where obsolete

Done when:

- there is a single authoritative transport composition per worker
- `REDIRECT_PROCESS_LOGS_TO_STDOUT` applies uniformly

## Milestone 5. Migrate Executor And Factory Code

### 5.1 Replace runner logging assembly

Tasks:

- update `RunnerJob`
- update `RunnerJobExecutor`
- replace `RunnerLog` usage with the new session/pump model

Done when:

- runner execution no longer requires `RunnerLog`
- subprocess output and agent-generated messages both use the new abstractions

### 5.2 Replace process log factory responsibilities

Tasks:

- shrink `ProcessLogFactory` into a small session factory or equivalent builder
- stop creating temp log directories for normal streaming
- keep mode selection based on runtime `segmentedLogs`

Done when:

- factory code is only responsible for session creation and mode selection
- no file-backed redirector remains in the main path

## Milestone 6. Remove Legacy Logging Pieces

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

### 6.2 Clean up remaining config/docs/tests

Tasks:

- update code comments to match new behavior
- update tests that referenced temp-file internals
- verify `logMaxDelay` docs describe flush interval semantics accurately

Done when:

- comments and docs no longer describe file polling behavior
- configuration behavior matches documentation

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
