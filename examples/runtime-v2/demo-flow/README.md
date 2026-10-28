## Runtime v2

This demo flow intends to highlight the salient features introduced by Concord's runtime-v2 and can act as a helpful
guide to migrate from v1 DSL to v2 syntax for Concord users/stakeholders.

The root `concord.yml` selects `concord-v2` and loads the flow and form fragments
from `concord/`. The Groovy script publishes `newVar` through `result`, and the
flow explicitly maps that output.

> The `errorFlow` flow has been commented as it will cause an error. Uncomment the flow lines
> to see error handling improvements introduced in runtime-v2.


