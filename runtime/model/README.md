# concord-runtime-model

Common interfaces for Concord runtimes. Declares top-level entities such as "process configuration", "profiles", etc.
Used in situations when support for multiple different but similarly structured runtimes is required (e.g.
`concord-v1` and `concord-v2` situations).

The `concord-v1` and `concord-v2` runtimes implements concord-runtime-model interfaces in
`com.walmartlabs.concord.runtime.*.wrapper` packages. Those implementations wrap the original model classes, mostly
due to some structural differences and us not wanting to change the base model classes in the runtimes too much.

Future runtimes can implement those interfaces directly in their respective model classes.
