# Manual

Manual triggers can be used to add items to the repository action drop down
in the Concord Console, similar to the default added _Run_ action.

Each `manual` trigger must specify the flow to execute using the `entryPoint`
parameter. The `name` parameter is the displayed name of the shortcut.

After repository triggers are refreshed, the defined `manual` triggers appear
as dropdown menu items in the repository actions menu.

```yaml
triggers:
- manual:
    name: Build
    entryPoint: main
- manual:
    name: Deploy Prod
    entryPoint: deployProd
- manual:
    name: Deploy Dev and Test
    entryPoint: deployDev    
    activeProfiles:
    - devProfile
    arguments:
      runTests: true
```

**Note:** standard [limitations](./index.md#limitations) apply.
