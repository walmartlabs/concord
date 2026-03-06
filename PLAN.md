# Logging Rewrite Plan

## Goal

Rewrite the agent module logging subsystem to reduce moving parts and make the code easier to reason about while preserving all current behavior, including behavior controlled by configuration flags and environment variables.

## Findings

### 1. Shutdown can silently lose logs

The runner path currently writes subprocess output into a local file and drains that file on a second thread. If the drain thread stalls, completion only logs a warning and cleanup can still remove the buffered log file. This makes end-of-process log loss possible without a hard failure.

Relevant classes:

- `agent/.../RunnerJobExecutor.java`
- `agent/.../RedirectedProcessLog.java`

### 2. Segmented log delivery failures are ignored

Segmented append and segment update methods return booleans, but the main consumer ignores those results. The code already hints at the problem with a `TODO: retry?`. This means segment data and terminal status updates can be dropped after retries are exhausted.

Relevant classes:

- `agent/.../SegmentedLogsConsumer.java`
- `agent/.../RemoteLogAppender.java`
- `agent/.../CombinedLogAppender.java`

### 3. `REDIRECT_PROCESS_LOGS_TO_STDOUT` is inconsistently applied

The env flag is wired into the DI-managed `LogAppender` set, but the worker-level `ProcessLog` is built with a private `RemoteLogAppender` instead of the bound composite appender. As a result, worker-stage messages such as repo export, imports, and state download are not mirrored to stdout when the flag is enabled.

Relevant classes:

- `agent/.../WorkerModule.java`
- `agent/.../Worker.java`

### 4. The main logging abstraction is internally inconsistent

`ProcessLog` mixes two different concerns:

- copying raw subprocess output
- writing formatted agent-generated log messages

`RemoteProcessLog` cannot support raw stream copying and throws `IllegalStateException`, while `RunnerLog` exists mostly to glue together two incompatible implementations.

Relevant classes:

- `agent/.../ProcessLog.java`
- `agent/.../RemoteProcessLog.java`
- `agent/.../RunnerLog.java`

### 5. Tests cover parsing better than assembled behavior

There are useful tests for segmented parsing and runner-side behavior, but there is no focused agent-level coverage around:

- `WorkerModule` wiring
- stdout mirroring
- log-drain completion behavior
- failure handling across the full pipeline

Relevant tests:

- `agent/.../SegmentedLogsConsumerTest.java`
- `agent/.../SegmentHeaderParserTest.java`
- `runtime/v2/runner-test/.../MainTest.java`

## Compatibility Requirements

The rewrite must preserve all of the following:

- Agent-level flush/buffering behavior controlled by `concord-agent.logMaxDelay`
- Agent-level `workDirMasking` propagation into runner config
- Runtime-specific `segmentedLogs` selection
- Runner-side `sendSystemOutAndErrToSLF4J`
- Env-gated stdout mirroring via `REDIRECT_PROCESS_LOGS_TO_STDOUT`
- System segment `0` behavior for unframed/system logs
- Warning/error counters and terminal segment status updates
- Existing retry policies for remote API calls
- Runtime-v1 non-segmented behavior
- Runtime-v2 segmented behavior

## Proposed Target Design

Replace the current split between file-backed buffering, parser, consumer, and mixed log abstractions with a single per-process logging pipeline.

### New core concepts

- `ProcessLogSession`
  - A per-process object owned by `Worker` or `RunnerJobExecutor`
  - Exposes only the operations actually needed by a caller

- `ProcessLogEvents`
  - Writes agent-generated `info/warn/error` messages

- `ProcessOutputSink`
  - Consumes raw subprocess bytes

- `ProcessLogTransport`
  - The delivery contract used by the session
  - Should expose:
    - `appendSystem(byte[])`
    - `appendSegment(long segmentId, byte[])`
    - `updateSegment(long segmentId, LogSegmentStats)`

- `CompositeProcessLogTransport`
  - Fans out to remote transport and optional stdout mirror

- `PlainOutputDecoder`
  - Pass-through decoder for non-segmented mode

- `SegmentedOutputDecoder`
  - Incremental decoder for framed runner output
  - Replaces the current parser plus consumer split

- `ProcessOutputPump`
  - Reads the subprocess output stream once
  - Pushes bytes through the selected decoder and transport

## Architectural Rules

The rewrite should follow these rules:

- One subprocess stream reader only
- No temporary log file for normal streaming
- No second thread rereading a local file
- No unsupported methods on core interfaces
- All process-visible logs use the same transport composition
- Failure handling is explicit and observable
- Configuration is resolved once and passed as a small mode/config object

## Staged Rewrite Plan

### Phase 1. Characterization tests

Add tests that lock in current externally visible behavior before production changes:

- runtime-v1 non-segmented runner output
- runtime-v2 segmented output
- agent-generated worker-stage messages
- invalid/unframed bytes landing in system segment `0`
- partial headers and partial payloads across chunk boundaries
- warning/error counts and final segment status updates
- stdout mirroring with `REDIRECT_PROCESS_LOGS_TO_STDOUT`
- completion and cancellation while output is still being delivered

Phase 1 deviation recorded after M1:

- a minimal subset of the later DI-composition work was pulled forward into M1
- specifically, `WorkerModule` was changed to build the worker-level `RemoteProcessLog` from the shared injected `LogAppender` composition instead of a private `RemoteLogAppender`
- this was done so the existing compatibility requirement for env-gated stdout mirroring could be locked in by characterization tests for worker-stage messages
- no other staged-plan deviations were identified during M1

### Phase 2. Introduce new transport layer

Add the new transport interfaces and implementations without changing call sites yet:

- `RemoteProcessLogTransport`
- `StdoutMirrorTransport`
- `CompositeProcessLogTransport`

At this stage:

- keep existing retry behavior
- make delivery results explicit
- remove boolean-return contracts from new interfaces
- capture transport failures in agent logs and metrics

### Phase 3. Replace file-backed streaming with direct pumping

Implement `ProcessOutputPump` and the decoder layer:

- `PlainOutputDecoder` for v1/non-segmented mode
- `SegmentedOutputDecoder` for v2/segmented mode

This phase should replace the need for:

- `LocalProcessLog`
- `RedirectedProcessLog`
- `RunnerJobExecutor.LogStream`

`logMaxDelay` should remain supported, but its meaning should become "flush interval / batching interval" instead of "poll delay while rereading a file".

### Phase 4. Normalize system-message delivery

Route agent-generated messages through the same session and transport stack used for subprocess output.

Recommended approach:

- in segmented mode, switch immediately to system segment `0` via the segmented API
- in non-segmented mode, preserve current user-visible behavior exactly

Do not keep the legacy whole-log API as an internal compatibility path for segmented mode. The rewrite target is a single segmented delivery path with system logs mapped to segment `0`.

### Phase 5. Switch call sites

Move current call sites onto the new session:

- `Worker`
- `WorkerModule`
- `ProcessLogFactory`
- `RunnerJobExecutor`
- `RunnerJob`

Expected simplifications:

- `RunnerLog` disappears
- `ProcessLogFactory` becomes a small session factory
- `WorkerModule` builds one composite transport and reuses it everywhere

### Phase 6. Remove obsolete code

After parity is proven, remove the old pieces:

- `RunnerLog`
- `RedirectedProcessLog`
- `LocalProcessLog`
- `AbstractProcessLog`
- `SegmentHeaderParser`
- `SegmentedLogsConsumer`
- unsupported `RemoteProcessLog.log(InputStream)` path

## Reliability Requirements For The New Design

The new pipeline must:

- never silently ignore a transport failure
- distinguish permanent `4xx` failures from retryable failures
- avoid deleting buffered state before delivery completes
- surface shutdown/drain failures clearly
- keep stdout mirroring best-effort and isolated from remote delivery

## Test Matrix

Add or preserve tests for:

- runtime-v1 non-segmented logging
- runtime-v2 segmented logging
- direct stdout output when `sendSystemOutAndErrToSLF4J=false`
- work-dir masking on and off
- stdout mirroring on and off
- invalid bytes before, between, and after segments
- every-byte chunk boundary fuzzing for segmented frames
- warning/error counter aggregation
- final segment status updates
- transient remote failures
- permanent remote failures
- process cancellation during heavy output
- worker-stage messages before runner startup

## Rollout Strategy

Use a two-step rollout:

- Step 1: land characterization tests and the new implementation behind current factory boundaries
- Step 2: switch all agent logging call sites to the new pipeline and delete dead code

This keeps the blast radius mostly inside the agent logging package, `WorkerModule`, and the runner executor.

## Resolved Scope Decisions

- In segmented mode, agent-generated messages switch immediately to system segment `0` using the segmented API.
- The rewrite scope is correctness only for now. Do not expand this change to add new delivery metrics, counters, or dashboards.
- `logMaxDelay` remains supported as an equivalent flush interval / batching bound. The rewrite does not need to preserve the exact current temp-file polling semantics.
