# Custom Task Example

This Maven project shows how to create a custom Concord task. It also shows how to use payload archives to test local changes.

## Usage

Run `test.sh` to test local changes:
```shell
./test.sh localhost:8001
```

The script builds the task JAR. It collects all runtime dependencies. It then creates a
[payload archive](https://concord.walmartlabs.com/docs/api/process.html#zip-file) with the [test flow](test.yml).

## Unit Tests

You can unit test Concord tasks without running them in a Concord environment.
See the [provided JUnit file](src/test/java/com/walmartlabs/concord/examples/customtask/CustomTaskTest.java).
