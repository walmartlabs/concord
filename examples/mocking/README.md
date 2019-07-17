Example of how to replace a runtime dependency with a "mock" version.
Can be useful for testing flows.

Note the ` -F activeProfiles=test` in `run.sh` -- activates the test
profile which loads the task replacement.