# Datetime

The `datetime` task provides methods to populate the current date at the 
time the flow runs.


The task is provided automatically by Concord and does not require any
external dependencies.

## Usage

The current date as a `java.util.Date` object:

```yaml
${datetime.current()} 
```

The current date/time from a specific zone formatted using the provided pattern:

```yaml
${datetime.currentWithZone('zone', 'pattern')}
${datetime.currentWithZone('America/Chicago', 'yyyy/MM/dd HH:mm:ss Z')}
```

Pattern syntax should follow
[the standard Java date/time patterns](https://docs.oracle.com/javase/8/docs/api/java/text/SimpleDateFormat.html).

The current date formatted as a string with a pattern: 

```yaml
${datetime.current('pattern')}
${datetime.current('yyyy/MM/dd HH:mm:ss')}
```

A `java.util.Date` instance formatted into a string:

```yaml
${datetime.format(dateValue, 'pattern')}
${datetime.format(dateValue, 'yyyy/MM/dd HH:mm:ss')}
```

Parse a string into a `java.util.Date` instance:

```yaml
${datetime.parse(dateStr, 'pattern')}
${datetime.parse('2020/02/18 23:59:59', 'yyyy/MM/dd HH:mm:ss')}
```

If no timezone specified, the `parse` method defaults to the current timezone
of a Concord agent running the process.
