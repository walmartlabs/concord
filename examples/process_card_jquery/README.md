# Process Cards

See the description in the [process_card_htmx](../process_card_htmx) example.

The historical directory name is retained, but this deployment-information form
now uses native browser validation and the restricted bridge instead of jQuery,
Semantic UI CDNs, direct API requests and parent-window navigation. The same
`release`, `field1`, `field2` and `field3` arguments and initial `values` are
supported. All executable assets must be bundled inline.
