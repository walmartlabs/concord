## Progress Notes

### 2026-03-06

- Read `PLAN.md` and `BACKLOG.md`.
- Confirmed Milestone 1 scope is characterization coverage only.
- Identified the current split logging paths:
  - worker-stage messages use `Worker` -> `ProcessLog` from `WorkerModule`
  - runner stdout/stderr uses `RunnerJobExecutor` -> `RunnerLog` -> `RedirectedProcessLog`
- Confirmed the known inconsistency called out in the plan:
  - `WorkerModule#getProcessLog()` always constructs `RemoteProcessLog` with `RemoteLogAppender`
  - stdout mirroring is only applied through DI-managed `LogAppender` composition used by runner logging
- Located current test coverage:
  - segmented parsing and aggregation are covered by `SegmentHeaderParserTest`
  - segmented consumer behavior is covered by `SegmentedLogsConsumerTest`
  - no focused worker-side characterization tests exist yet
  - no focused drain/cancellation characterization tests exist yet
- Implemented the small production wiring change:
  - `WorkerModule#getProcessLog(...)` now uses the injected `LogAppender` composition instead of creating an isolated `RemoteLogAppender`
- Added Milestone 1 test scaffolding:
  - `WorkerLoggingTest` for worker-stage messages emitted before runner startup
  - `WorkerModuleTest` for worker-stage stdout mirroring through the shared appender composition
  - `ProcessLogCharacterizationTest` for non-segmented delivery, segmented chunk-boundary handling, invalid bytes -> segment `0`, segment stats/status propagation, shutdown while a chunk is in flight, and stop/cancel-style drain completion with buffered unread bytes
- Added repo instructions in `AGENTS.md` for:
  - periodic `PROGRESS.md` updates
  - separate commits for small production changes
  - git commit message format `module-name: short description`
  - `var` usage in new Java files without mixing local declaration styles
  - assigning complex setup objects to named locals instead of inlining them
- Verification:
  - focused new/affected tests passed
  - full `./mvnw -Dmaven.repo.local=/tmp/m2 -pl agent test` passed
- Separate production commit created:
  - `a8f529062` `agent: use shared log appender for worker logs`
- Next implementation steps:
  - leave the Milestone 1 test/doc changes ready for review
