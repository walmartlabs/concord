# Development Notes

## Concord Server and Server Plugins

Prefer explicit binding using `com.google.inject.Module` over `@Named` annotations.
Use `@Named` for top-level modules and server plugins.

Some classes require explicit bindings using `Multibinder.newSetBinder`:
- com.walmartlabs.concord.server.sdk.ScheduledTask
