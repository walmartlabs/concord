# concord-runtime-model

Common interfaces for Concord runtimes. Declares top-level entities such as "process configuration", "profiles", etc.
Used to share a process-definition model across Concord runtime components.
Runtime-specific implementations provide the interfaces in
`com.walmartlabs.concord.runtime.*.wrapper` packages.

Future runtimes can implement those interfaces directly in their respective model classes.
