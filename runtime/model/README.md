# concord-runtime-model

This module defines common interfaces for Concord runtimes.
It defines top-level entities such as process configuration and profiles.
Use these interfaces when a system must support multiple runtimes with similar structures.
For example, the system can support `concord-v1` and `concord-v2`.

The `concord-v1` and `concord-v2` runtimes implement these interfaces in the `com.walmartlabs.concord.runtime.*.wrapper` packages.
These implementations wrap the original model classes because their structures are different.
This design prevents major changes to the base model classes in each runtime.
Future runtimes can implement these interfaces directly in their model classes.
