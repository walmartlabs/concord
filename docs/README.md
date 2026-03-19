# Concord Documentation

This documentation covers Concord, a workflow server and orchestration engine that connects different
systems together using scenarios and plugins. 

Concord provides a comprehensive platform for process automation, featuring:

- **Process Management**: execute workflows in isolated environments
- **Project Organization**: group processes with shared configuration and resources
- **Security**: built-in secrets management and team-based authorization
- **Extensibility**: rich plugin ecosystem for system integration

## Building

To build and view the documentation:

1. Install the required tools:
   ```shell
   cargo install mdbook mdbook-variables
   ```

2. Serve the documentation locally:
   ```shell
   mdbook serve
   ```

   The documentation will be available at `http://localhost:3000`

The built documentation is output to the `book/` directory.
