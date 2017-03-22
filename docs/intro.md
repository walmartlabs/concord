# Introduction

Concord is a workflow server. It is a "glue", connecting different
systems together using scenarios and plugins created by users.

## Overview

Concord consists of the following major components:

1. [server](#server) - provides a REST API for managing projects,
   templates and repositories. It receives and processes user requests
   to call workflow scenarios;
2. [agent](#agent) - a (remote) workflow executor. Receives scenarios
   from the server and executes them in an isolated environment;
3. [console](#console) - web UI for managing and monitoring the server
   and its processes.
   
### Server

The server provides several REST API endpoints for managing it's data,
they are described in a [separate document](./api).

The main purpose of the server is to receive a workflow process
definition and its dependencies from an user and execute it remotely
using the agent.

Workflow process definitions, its dependencies and supporting files
collected in a single archive file called "payload".

There are several ways to start a process:

- send a complete, self-contained ZIP archive to the server. Its
format is described in a
[separate document](./processes.md#payload-format);
- send a JSON request, containing only request parameters and a
reference to a [project](#project) or a [template](#template);
- same as **2**, but sending additional files with the request.

For methods **2** and **3**, the server will build a payload archive
itself. Those methods are particularly useful when used with projects
and templates.

*TBD*

### Project

Projects allow users to automatically create payloads by pulling files
from remote GIT repositories and applying templates.

Projects are created using the REST API or (in the near future) the UI.

*TBD*

### Template

[See also](./templates.md).

*TBD*

### Agent

The agent is a standalone Java application that receives and executes
a [payload](#payload) sent by the server.

*TBD*

### Console

The console is a web application for managing and monitoring the
server.

*TBD*

### How it all works together

![Overview](images/runtime-overview.png)

Here is the simplified version of how Concord and its processes work:

1. user publishes an archive containing process definitions, dependencies and
other files using **concord-server** HTTP API;
2. **concord-runner** is added to the archive. It's an executable that contains
the BPM engine and its supporting dependencies;
3. the updated archive is published to one of the available instances of **concord-agent**;
4. **concord-agent** spawns a new JVM and executes the archive;
5. logs are streamed back to **concord-server**.

