Example of how to replace a runtime-v2 task dependency with a "mock" version.
The test profile loads the `Task` implementation from `mocks` using `loadTasks`.
The mock throws an exception, which the flow handles using `lastError`.

Note the ` -F activeProfiles=test` in `run.sh` -- activates the test
profile which loads the task replacement.