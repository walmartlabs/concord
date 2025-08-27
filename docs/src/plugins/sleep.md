# Sleep

The `sleep` task provides methods to make the process wait or suspend for a
certain amount of time.

The task is provided automatically by Concord and does not require any
external dependencies.

## Usage

Sleep for a specific amount of time, for example 10000 ms (10s):

```yaml
- ${sleep.ms(10000)}
```

or using the full task syntax:

```yaml
- task: sleep
  in:
    duration: 10
```

Alternatively, an [ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) timestamp
can be used to specify the time in the future until the process should sleep:

```yaml
- task: sleep
  in:
    until: "2019-09-10T16:00:00+00:00"
```

If the `until` value is in the past, Concord logs a warning message `Skipping
the sleep, the specified datetime is in the past`.

## Suspend process for sleep duration

Sleeping for long durations wastes Agent resources. The process can be suspended
for the duration to free the Agent to run other processes in the meantime.

```yaml
- task: sleep
  in:
    suspend: true
    duration: ${60 * 5} # 5 minutes
```

Instead of waiting for the specified time, the process can be suspended and
resumed at the later date:

```yaml
- task: sleep
  in:
    suspend: true
    until: "2019-09-10T16:00:00+00:00"
```
