# Development

## Running from an IDE

You can start the server and the agent directly from an IDE using the following main
classes:
- concord-server: `com.walmartlabs.concord.server.Main`
- concord-agent: `com.walmartlabs.concord.agent.Main`

To use predefined project templates, the server must be started with `DEPS_STORE_DIR`
environment variable pointing to the `server/impl/target/deps` directory.

To start the UI, please refer to the console's [readme file](../console/README.md).

## Debugging

The `concord-server` and `concord-agent` processes are plain Java processes and can be
started in debug mode as usual.

However, as the agent processes its payload in a separate JVM, it must be configured to
start those processes with the remove debugging enabled: *TBD*