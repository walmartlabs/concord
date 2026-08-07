# runtime-v2.5 (proof of concept)

A from-scratch runtime: YAML model (`model`), continuation-fiber engine (`runner`), and the
distribution entry point (`runner-dist`). Behavioral baseline is runtime-v2; this document lists
the intentional differences, limits, and operational constraints. See `PLAN.md` (repo root) for
the remediation history.

## Java version split

- `concord-runtime-model-v2.5` is compiled at **Java 17** — it is linked into release-17 artifacts
  (server, CLI lint/parse paths).
- `concord-runner-v2.5` / `concord-runner-v2.5-dist` require **Java 25** (virtual threads and
  newer APIs). Local `concord run` of a `concord-v2.5` project therefore needs a JDK 25 CLI;
  linting (including semantic expression and literal flow-call validation) works on JDK 17.

## Durable state

- State files carry a `CV25` magic header, a format version (currently **4**), and a SHA-256 over
  the serialized body. Corrupted bodies are rejected with `StateFormatException`; format
  mismatches are rejected outright (no migration between PoC formats).
- Sensitive values are not serialized into state. Remote runs persist them separately in the
  session-file channel; local CLI runs use a sibling `<stateFile>.secrets.json` channel, both
  restored before resume or checkpoint restart.
- Deserialization runs through an `ObjectInputFilter` with hard limits: depth **128**,
  **1,000,000** object references, **1,000,000** array elements, and **64 MiB** of stream data.
  The same limits apply when state is *written* (write-time verification); a state exceeding them
  fails the process with an error naming the exceeded limit. Known deserialization gadget anchors
  are denied, while dependency task DTOs remain supported.
- Deserialization uses the process dependency classloader, so task DTOs from dependency jars
  round-trip through suspend/checkpoint.
- Writes are validated by re-reading and validating the serialized copy before the file is
  replaced, so a hostile `readResolve` cannot poison checkpoints.

## Reserved names

- `suspend` is a reserved checkpoint name (the engine's internal suspension sentinel). The parser
  rejects `checkpoint: suspend` with a `V25_RESERVED_CHECKPOINT` diagnostic.

## Nested flows

`nestedFlowExecutor().execute(...)` runs a flow synchronously inside the calling task. A nested
flow **cannot suspend or checkpoint** — reaching either safe point inside a nested flow fails the
step with a clear error. Keep nested flows short and side-effect-local.

## Variables and values

- Scope values are deep-frozen containers (maps/lists/sets/arrays are copied and made immutable).
  Non-container Serializable leaves (task POJOs, `java.util.Date`, …) are stored **by reference**
  and shared across parallel branches — treat them as immutable.
- `threadId` in task results is a compatibility shim and is always `0`; runtime-v2.5 does not
  expose v2's thread model.
- Expression evaluation is strict about unknown identifiers: referencing an undefined variable
  fails with `PropertyNotFoundException`. Use `${hasVariable('x')}` guards for optional values.

## Dry-run

Tasks without `@DryRunReady` are rejected in dry-run mode, evaluated against the task object
actually created (after provider priority resolution). `MockTask` is `@DryRunReady`, so mocked
tasks always run in dry-run regardless of the origin task's annotation.
