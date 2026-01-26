# OneOps

- [Version 2](#oneops-v2)
- [Version 1](#oneops-v1)
- [Migration](#oneops-migration)

Using `oneops` as an event source allows Concord to receive events from
[OneOps](https://oneops.github.io). You can configure event properties in the OneOps
notification sink, specifically for use in Concord triggers.

Currently Concord supports two different implementations of `oneops` triggers:
`version: 1` and `version: 2`.

<a name="oneops-v2"/>

## Version 2

Deployment completion events can be especially useful:

```yaml
flows:
  onDeployment:
  - log: "OneOps has completed a deployment: ${event}"
  
triggers:
- oneops:
    version: 2
    conditions:
      org: "myOrganization"
      asm: "myAssembly"
      env: "myEnvironment"
      platform: "myPlatform"
      type: "deployment"
      deploymentState: "complete"
    useInitiator: true
    entryPoint: onDeployment
```

The `event` object, in addition to its trigger parameters, contains a `payload`
attribute--the original event's data "as is". You can set `useInitiator` to
`true` in order to make sure that process is initiated using `createdBy`
attribute of the event.

The following example uses the IP address of the deployment component to build 
an Ansible inventory for execution of an [Ansible task]({{ site.concord_plugins_v2_docs }}/ansible.md):

```yaml
flows:
  onDeployment:
  - task: ansible
    in:
      ...
      inventory:
        hosts:
          - "${event.payload.cis.public_ip}"
```

**Note:** standard [limitations](./index.md#limitations) apply.

<a name="oneops-v1"/>

## Version 1

```yaml
flows:
  onDeployment:
  - log: "OneOps has completed a deployment: ${event}"

triggers:
- oneops:
    version: 1 # optional, depends on the environment's defaults
    org: "myOrganization"
    asm: "myAssembly"
    env: "myEnvironment"
    platform: "myPlatform"
    type: "deployment"
    deploymentState: "complete"
    useInitiator: true
    entryPoint: onDeployment
```

**Note:** standard [limitations](./index.md#limitations) apply.

<a name="oneops-migration"/>

### Migrating OneOps trigger from v1 to v2

In `version: 2`, the trigger conditions are moved into a `conditions` field:

```
# v1
- oneops:
    org: "myOrganization"
    asm: "myAssembly"
    env: "myEnvironment"
    platform: "myPlatform"
    type: "deployment"
    deploymentState: "complete"
    useInitiator: true
    entryPoint: onDeployment
# v2
- oneops:
    conditions:
      org: "myOrganization"
      asm: "myAssembly"
      env: "myEnvironment"
      platform: "myPlatform"
      type: "deployment"
      deploymentState: "complete"
    useInitiator: true
    entryPoint: onDeployment
```