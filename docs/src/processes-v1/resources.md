# Resources

Resource directory path such as `./concord` can be configured in
the `resources` top-level element in the concord file.

Concord loads the root `concord.yml` first and subsequently looks for the
resources paths under the `resources` section.

The following resources configuration causes all flows to be loaded
from the `myFlows` folder instead of the default `concord` folder
using the pattern `./concord/**/*.yml`.

```yaml
resources:
  concord: "myFlows"
```

Multiple resource paths per category are also supported:

```yaml
resources:
  concord:
    - "myFlowDirA"
    - "myFlowDirB"
```

Resource loading can be disabled by providing the list of disabled resources.

```yaml
resources:
    concord: "myFlows"
    disabled:
      - "profiles" # deprecated folders
      - "processes"
```

In the above example, flows are picked from `./myFlows` instead of the `./concord`
directory and loading of resources from the `./profiles` and `./processes`
directories is disabled.
