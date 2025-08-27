# Docker

Concord supports running [Docker](https://hub.docker.com/) images within a process flow.

- [Usage](#usage)
- [Parameters](#parameters)
- [Environment Variables](#environment-variables)
- [Docker Options](#docker-options)
    - [Add Host Option](#add-host-option)
- [Capturing the Output](#capturing-the-output)
- [Custom Images](#custom-images)
- [Limitations](#limitations)

## Usage

The `docker` task is called with standard
[runtime-v2 task call syntax](../processes-v2/flows.html#task-calls).

```yaml
flows:
  default:
    - task: docker
      in:
        image: library/alpine
        cmd: echo '${greeting}'
      out: dockerResult

configuration:
  arguments:
    greeting: "Hello, world!"
```

The above invocation is equivalent to running

```bash
docker pull library/alpine && \
docker run -i --rm \
-v /path/to/process/workDir:/workspace \
library/alpine \
echo 'Hello, world!'
```

## Parameters

- `image` - mandatory, string. Docker image to use;
- `cmd` - optional, string. Command to run. If not specified, the image's
`ENTRYPOINT` is used;
- `env` - optional, [environment variables](#environment-variables);
- `envFile` - optional. Path to the file containing
[environment variables](#environment-variables);
- `hosts` - optional. Additional [/etc/host entries](#add-host-option);
- `forcePull` - optional, boolean. If `true` Concord runs
`docker pull ${image}` before starting the container. Default is `true`;
- `debug` - optional, boolean. If `true` Concord prints out additional
information into the log (the command line, parameters, etc);
- `redirectErrorStream` - optional boolean. Redirect container error output to standard output. Default is `false`; 
- `logOut` - optional boolean. Sends container standard output to Concord process logs. Default is `true`;
- `logErr` - optional boolean. Sends container error output to Concord process logs. Default is `true`;
- `saveOut` - optional boolean. Save container standard output in task result, as `stdout` variable. Default is `false`;
- `saveErr` - optional boolean. Save container error output in task result, as `stderr` variable. Default is `false`;
- `pullRetryCount` - optional, number. Number of retries if `docker pull`
fails. Default is `3`;
- `pullRetryInterval` - optional, number. Delay in milliseconds between
`docker pull` retries. Default is `10000`.

**Note:** The current process' working directory is mounted as `/workspace`.
Concord replaces the container's `WORKDIR` with `/workspace`. Depending
on your setup, you may need to change to a different working directory:

```yaml
- task: docker
  in:
    image: library/alpine
    cmd: cd /usr/ && echo "I'm in $PWD"
``` 

To run multiple commands multiline YAML strings can be used:

```yaml
- task: docker
  in:
    image: library/alpine
    cmd: |
      echo "First command"
      echo "Second command"
      echo "Third command"
```

Concord automatically removes the container when the command is complete.

## Environment Variables

Additional environment variables can be specified using `env` parameter:

```yaml
flows:
  default:
  - task: docker
    in:
      image: library/alpine
      cmd: echo $GREETING
      env:
        GREETING: "Hello, ${name}!"

configuration:
  arguments:
    name: "concord"
```

Environment variables can contain expressions: all values will be
converted to strings.

A file containing environment variables can be used by specifying
the `envFile` parameter:

```yaml
flows:
  default:
  - task: docker
    in:
      image: library/alpine
      cmd: echo $GREETING
      envFile: "myEnvFile"
```

The path must be relative to the process' working directory `${workDir}`.

It is an equivalent of running `docker run --env-file=myEnvFile`.

## Docker options

### Add Host Option

Additional `/etc/hosts` lines can be specified using `hosts` parameter:

```yaml
flows:
  default:
  - task: docker
    in:
      image: library/alpine
      cmd: echo '${greeting}'
      hosts:
        - foo:10.0.0.3
        - bar:10.7.3.21

configuration:
  arguments:
    greeting: "Hello, world!"
```

## Capturing the Output

The `stdout` and `stderr` attributes of the task's returned data can be used to
capture the output of commands running in the Docker container:

```yaml
- task: docker
  in:
    image: library/alpine
    cmd: echo "Hello, Concord!"
    saveOut: true
  out: dockerResult

- log: "Got the greeting: ${dockerResult.stdout.contains('Hello')}"
```

In the example above, the output (`stdout`) of the command running in the
container is accessible in the returned object's `stdout` attribute.

The `stderr` parameter can be used to capture the errors of commands running
in the Docker container:

```yaml
- task: docker
  in:
    image: library/alpine
    cmd: echo "Hello, ${name}" && (>&2 echo "STDERR WORKS")
    saveErr: true
  out: dockerResult

- log: "Errors: ${dockerResult.stderr}"
```

In the example above the errors (`stderr`) of the command running in the
container is accessible in the returned object's `stderr` variable.

## Custom Images

Currently, there's only one requirement for custom Docker images: all images
must provide a standard POSIX shell as `/bin/sh`.

## Limitations

Running containers as `root` user is not supported - all user containers are
executed using the `concord` user equivalent to a run command like `docker run
-u concord ... myImage`.  The user is created automatically with UID `456`.

As a result any operations in the docker container that require root access,
such as installing packages, are not supported on Concord. If required, ensure
that the relevant package installation and other tasks are performed as part of
your initial container image build and published to the registry from which
Concord retrieves the image.
