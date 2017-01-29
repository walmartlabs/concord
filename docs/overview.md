# Overview of the Concord's Architecture

## How it works

![Overview](images/runtime-overview.png)

Here is the simplified version of how Concord and its processes work:

1. user publishes an archive containing process definitions, dependencies and
other files using **concord-server** HTTP API;
2. **concord-runner** is added to the archive. It's an executable that contains
the BPM engine and its supporting dependencies;
3. the updated archive is published to one of the available instances of **concord-agent**;
4. **concord-agent** spawns a new JVM and executes the archive;
5. logs are streamed back to **concord-server**.