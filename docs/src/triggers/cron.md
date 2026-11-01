# Cron Triggers

You can schedule execution of flows by defining one or multiple `cron` triggers.

Each `cron` trigger is required to specify the flow to execute with the
`entryPoint` parameter. Optionally, key/value pairs can be supplied as
`arguments`.

The `spec` parameter is used to supply a regular schedule to execute the
flow by using a [CRON syntax](https://en.wikipedia.org/wiki/Cron).

The following example trigger kicks off a process to run the `hourlyCleanUp`
flow whenever the minute value is 30, and hence once an hour every hour.

```yaml
flows:
  hourlyCleanUp:
    - log: "Sweep and wash."
triggers:
  - cron:
      spec: "30 * * * *"
      entryPoint: hourlyCleanUp
```

Multiple values can be used to achieve shorter intervals, e.g. every 15 minutes
with `spec: 0,15,30,45 * * * *`. A daily execution at 9 can be specified with
`spec: 0 9 * * *`. The later fields can be used for hour, day and other
values and advanced [CRON](https://en.wikipedia.org/wiki/Cron) features such as
regular expression usage are supported as well.

Cron triggers that include a specific hour of day, can also specify a timezone 
value for stricter control. Otherwise the Concord instance specific timezone is used.

```yaml
flows:
  cronEvent:
    - log: "On cron event."
triggers:
  - cron:
      spec: "0 12 * * *"
      timezone: "Europe/Moscow"
      entryPoint: cronEvent
```

Values for the timezone are derived from the
[tzdata](https://en.wikipedia.org/wiki/Tz_database)
database as used in the
[Java TimeZone class](https://docs.oracle.com/javase/8/docs/api/java/util/TimeZone.html).
You can use any of the TZ values from the
[full list of zones](https://en.wikipedia.org/wiki/List_of_tz_database_time_zones).

Each trigger execution receives an `event` object with the properties `event.fireAt`
and `event.spec` as well as any additional arguments supplied in the
configuration (e.g. `arguments` or `activeProfiles`): 

```yaml
flows:
  eventOutput:
    - log: "${name} - event run at ${event.fireAt} due to spec ${event.spec} started."
triggers:
  - cron:
      spec: "* 12 * * *"
      entryPoint: eventOutput
      activeProfiles:
      - myProfile
      arguments:
        name: "Concord"
```

Scheduled events are a useful feature to enable tasks such as regular cleanup
operations,  batch reporting or processing and other repeating task that are
automated via a Concord flow.

**Note:** standard [limitations](./index.md#limitations) apply.

## Running as a Specific User

Cron-triggered processes run as a system `cron` user by default. This user may
not have access to certain resources (e.g. Secrets, JSON Store). A user's API
key can be referenced from a project-scoped single-value (string)
[Secret](../console/secret.md) to run
the process as the user.

```yaml
triggers:
- cron:
    spec: "* 12 * * *"
    entryPoint: cronEvent
    runAs:
      # secret must be scoped to the project and contain the API token of the initiator
      withSecret: "user-api-key-secret-name"
```
