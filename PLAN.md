# Concord CLI Local Suspend/Resume Plan

## Summary

Goal: add local runtime-v2 suspend/resume support in two commits:

1. Generic local suspend/resume for arbitrary runtime-v2 events.
2. Standard form UX built on top of that generic resume path.

This changes the original direction. The first cut is no longer an in-process form loop inside `concord run`. Instead:

- `concord run` starts the process locally as it does today.
- If the process suspends, `run` persists local state and prints guidance.
- A new `concord resume [<path>]` command resumes the prepared workspace.
- Form handling is added later as a specialized mode of `concord resume`.

## Status

- Commit 1 is implemented.
- Commit 2 is not started.
- The plan below reflects the shipped Commit-1 behavior, including two intentional deviations from the original draft:
  - `concord resume` accepts an optional path and infers the default local workspace from the current directory.
  - generic resume input supports both file-based payloads and inline `key=value` arguments.

## Key Decisions

- Add a new top-level CLI subcommand: `concord resume [<path>]`.
- Do not use `concord run --resume`. `run` is source-oriented and prepares a target workspace; resume must operate on an existing prepared workDir with persisted state.
- Keep the current CLI service wiring. Do not switch the CLI over to `runtime.v2.runner.Main`.
- Commit 1 supports one selected event per `concord resume` invocation.
- If `concord resume` is called without a path:
  - first try the current directory as the prepared workspace
  - otherwise try the default assembled workspace under the current directory
- Suspend guidance should be user-oriented:
  - prefer the directory containing `concord.yml` when the prepared workspace is that directory's default `target/`
  - otherwise show the actual prepared workspace path
- Commit-1 resume payload supports structured input from either:
  - `--input-file <json|yaml>`
  - `-e/--extra-vars key=value`
  - optional `--save-as <path>` applied to either source
- Commit 2 layers standard-form UX on top of `concord resume`:
  - if pending standard forms exist and no manual generic payload flags are present, enter interactive form mode
  - otherwise stay in generic resume mode

## Current Behavior To Preserve

- `concord run` still prepares a target work directory, resolves dependencies, loads the project, creates the runtime injector, and starts the process locally.
- Exit code behavior remains aligned with current handling for successful completion and runtime failures.
- Existing verbose logging and task/flow logging behavior stays unchanged outside the new suspend/resume path.
- Local scope only:
  - runtime-v2 only
  - no remote resume
  - no live-server form UX
  - no checkpoint restore UX in this work

## Commit 1: Generic Local Suspend/Resume

### User-visible behavior

- `concord run <dir>`:
  - starts normally
  - if the process finishes, behaves as today
  - if the process suspends, persists local state, prints waiting event refs and an exact resume command, and exits `0`
- Guidance message should include the exact resume command, e.g.:
  - `Process suspended.`
  - `Waiting event: <eventRef>` or `Waiting events: <eventRef1>, <eventRef2>`
  - `Resume with: concord resume --event <eventRef>`
  - `Resume with: concord resume <path> --event <eventRef>` when an explicit path is needed

- `concord resume [<path>]`:
  - resumes a previously suspended local process
  - when `<path>` is omitted:
    - use the current directory if it contains suspended local state
    - otherwise use the default prepared workspace under the current directory
  - accepts:
    - `--event <eventRef>`
    - `--input-file <path>`
    - `-e/--extra-vars key=value`
    - `--save-as <path>`
  - if exactly one event is waiting and `--event` is omitted, use it automatically
  - if multiple events are waiting and `--event` is omitted, fail with a clear list of available events

Examples:

```bash
concord run my-flow/
cd my-flow/
concord resume --event ev1
concord resume /tmp/custom-workdir --event ev1
concord resume --event ev1 -e myForm.value=hello
concord resume --event ev1 --input-file payload.yml
concord resume --event ev1 --input-file payload.yml --save-as myForm
```

### Implementation changes

- Update `Run.call()` to capture the `ProcessSnapshot` returned by `runner.start(...)`.
- Add a small CLI-local suspension persistence helper responsible for:
  - persisting the suspended snapshot via `StateManager.saveProcessState(...)`
  - writing the waiting event refs via `StateManager.finalizeSuspendedState(...)` or equivalent helper usage
  - persisting CLI resume metadata needed to rebuild the local runtime consistently

- Persist CLI resume metadata in a CLI-only file under `_attachments/_state`, containing at least:
  - `instanceId`
  - `entryPoint`
  - active profiles
  - resolved dependency coordinates
  - runner/api/logging settings needed to recreate the injector
  - preferred resume directory for user-facing guidance
  - workDir path

- Add `cli/src/main/java/com/walmartlabs/concord/cli/Resume.java` and register it in `App`.
- `Resume.call()` must:
  - resolve `<path>` into an actual prepared workspace
  - validate that the resolved workspace contains suspended local state
  - load the persisted `ProcessSnapshot`
  - read waiting event refs from `_attachments/_state/_suspend`
  - validate event selection
  - load `--input-file` as a top-level JSON or YAML object when present
  - load `-e/--extra-vars key=value` as inline structured input, with dotted keys creating nested maps
  - apply `--save-as` wrapping when present
  - rebuild the runtime injector from the persisted CLI metadata
  - call `Runner.resume(snapshot, Set.of(eventRef), input)`

- After `Runner.resume(...)` returns:
  - if suspended again:
    - persist updated snapshot and events
    - print the next resume guidance message
    - exit `0`
  - if finished:
    - clean `_attachments/_state`
    - print `...done!`
    - exit `0`

### Error handling

- Fail clearly on:
  - missing snapshot
  - missing suspend marker
  - unknown event
  - ambiguous event choice
  - invalid or non-object payload file
  - missing CLI resume metadata

## Commit 2: Standard Forms On Top Of `concord resume`

### User-visible behavior

- `concord run` remains detached. It does not start prompting inline for forms.
- When suspension includes standard forms, `run` prints:
  - the pending form name(s)
  - the workspace path
  - the recommended `concord resume <workDir>` command

- Extend `concord resume <workDir>` with form-aware default behavior:
  - if no generic manual flags are present and pending standard forms exist in `_attachments/_state/V2forms`, enter interactive form mode
  - if generic manual flags are present, stay in generic mode even if forms exist

- Form mode behavior:
  - read pending standard forms from `_attachments/_state/V2forms`
  - process forms sequentially in sorted form-name order
  - render prompts in the terminal
  - convert and validate with shared forms code
  - resume one form event at a time
  - re-read pending forms after each resume

### Form-specific rules

- Reuse `com.walmartlabs.concord.forms.FormUtils` and `DefaultFormValidator`.
- Use `DefaultFormValidatorLocale` for local messages.
- Keep effective submitted data under the form name:
  - `{ formName -> convertedFieldValues }`
- Handle file fields by:
  - passing an `InputStream` into form conversion
  - moving temp files into `workDir/_form_files/<formName>/<fieldName>`
  - leaving the resumed field value as the workspace-relative `_form_files/...` path
- Do not pass the `FORM_FILES` marker into `Runner.resume(...)`.
- Fail fast on custom form assets under `forms/<formName>/`.
- Treat `yield` as informational only for local CLI.
- Treat `runAs` as out of scope for local security semantics in this phase; document any reduced local behavior explicitly if needed during implementation.

### Terminal behavior

- Print a clear header before prompting:
  - `Process suspended on form: <name>`
- Show field label or name, type, optional/required status, allowed values, and default value when present.
- Do not prompt readonly fields.
- Support repeated prompts for multi-value cardinalities.
- Support hidden input for password fields when `System.console()` is available.
- On conversion or validation errors, print field-specific errors and restart the same form with previously entered values preserved where practical.

## Tests

### Commit 1 CLI tests

Given the request to keep tests minimal, the implemented Commit-1 CLI coverage is:

- suspended `concord run` persists state and prints resume guidance instead of `...done!`
- `concord resume --event ev1 --input-file payload.yml --save-as myForm` resumes successfully from the project directory with inferred workspace resolution
- `concord resume --event ev1 -e myForm.value=...` resumes successfully with inline nested input

Additional useful Commit-1 coverage, still worth adding later if needed:

- multiple waiting events require explicit `--event`
- reentrant-task resume still works
- missing or invalid suspended state fails clearly
- resuming into another suspend persists the updated state again

### Commit 2 CLI tests

- single standard form happy path
- validation failure and successful retry
- optional scalar fields left blank
- readonly field not prompted and still present in process variables
- boolean parsing from `y/n`
- file field upload copies contents into `_form_files/...` and the process can read it
- multiple pending forms are resumed sequentially
- pending custom form assets return an unsupported error
- mixed form and non-form suspension leaves remaining generic events for manual resume

### Runtime compatibility assertions

- generic resumed payload reaches the matching suspended thread only
- submitted form values are available under `${myForm.*}`
- uploaded file fields resolve to workspace-relative `_form_files/...` paths
- `workDir` and `txId` remain intact across resume

## Acceptance Criteria

- `concord run <dir>` can suspend a runtime-v2 process locally, persist resumable state, and print a correct resume command.
- `concord resume [<path>]` can resume a generic suspended event using optional structured payload input from either a JSON/YAML file or inline `key=value` arguments.
- The first commit works for non-form suspend points and does not depend on form support.
- Form support can be added in a second commit without changing the generic resume command shape.
- Standard runtime-v2 form flows can be completed locally via `concord resume <workDir>` interactive form mode.
- Custom forms fail fast with a clear message.

## Explicit Assumptions

- Target file: root-level `PLAN.md`.
- First implementation is runtime-v2 only.
- First implementation is local-only.
- First implementation supports one selected event per `concord resume` invocation.
- Commit 1 payload input supports file-based structured input and inline `key=value` input, plus optional `--save-as`.
- Form-specific automatic nesting, conversion, validation, and file-upload handling are second-commit work.
- A suspended `concord run` exiting `0` is acceptable because it produced resumable state rather than a runtime error.
