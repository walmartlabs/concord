# Custom Task Example

An example Maven project which shows how to create a custom Concord task and use payload archives to test local changes.

## Usage

Run `test.sh` to test local changes:
```yaml
$ ./test.sh concord.example.com
```

The script builds the task's JAR, collects all runtime dependencies and creates a
[payload archive](https://concord.walmartlabs.com/docs/api/process.html#zip-file) with the [test flow](test.yml).

## Unit Tests

Generally speaking, Concord tasks can be unit tested without running them in a Concord environment.
See the [provided JUnit file](src/test/java/com/walmartlabs/concord/examples/customtask/CustomTaskTest.java).
