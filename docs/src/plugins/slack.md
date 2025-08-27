# Slack

The `slack` plugin supports interaction with the [Slack](https://slack.com/)
messaging platform.

- posting messages to a channel with the [slack task](#slack)
- working with channels and groups with the [slack channel task](#slackChannel)

The task is provided automatically for all flows, no external dependencies
necessary.

## Configuration

The plugin supports default configuration settings supplied by 
[default process configuration policy](../getting-started/policies.html#default-process-configuration-rule):

```json
{
  "defaultProcessCfg": {
    "defaultTaskVariables": {
      "slack": {
        "apiToken": "slack-api-token",
        "proxyAddress": "proxy.example.com",
        "proxyPort": 123
      }
    }
  }
}
```

The bot user created for the API token configuration e.g. `concord` has to be a
member of the channel receiving the messages.

## Common Parameters

Common parameters of both `slack` and `slackChannel` tasks:
- `apiToken`: required, the
  [slack API token](https://api.slack.com/custom-integrations/legacy-tokens)
  for authentication and authorization. The owner of the token as has to have
  sufficient access rights to create or archive channels and groups. Typically
  this should be provided via usage of the [Crypto task](./crypto.html) or
  configured in the [default variables](../getting-started/policies.html#default-process-configuration-rule);
- `proxyAddress`: optional, the proxy's host name;
- `proxyPort`: optional, the proxy's port.

<a name="slack"/>

## Slack Task

Possible operations are:

- [Send Message](#send-message)
- [Add Reaction](#add-reaction)

### Send Message

A message `text` can be sent to a specific channel identified by a `channelId`
with the standard [runtime-v2 task call syntax](../processes-v2/flows.html#task-calls).

```yaml
flows:
  default:
    - task: slack
      in:
        channelId: "exampleId"
        username: "anyCustomString"
        iconEmoji: ":information_desk_person:"
        text: "Starting execution on Concord, process ID ${txId}"
        ignoreErrors: true
      out: result

    - if: "${!result.ok}"
      then:
        - log: "Error while sending a message: ${result.error}"

    ...

    - task: slack
      in:
        channelId: "exampleId"
        ts: ${result.ts}
        replyBroadcast: false
        username: "anyCustomString"
        iconEmoji: ":information_desk_person:"
        text: "Execution on Concord for process ID ${txId} completed."
        ignoreErrors: true
```

The `channelId` can be seen in the URL of the channel or alternatively the name
of the channel can be used e.g. `C7HNUMYQ1` and `my-project-channel`. To send a
message to a specific user use `@handle` syntax:

```yaml
- task: slack
  in:
    channelId: "@someone"
    text: "Hi there!"
```

> Though using `@handle` does work, it stops working, if the user changes the _Display Name_
of their Slack profile.

Optionally, the message sender name appearing as the user submitting the post,
can be changed with `username`.  In addition, the optional `iconEmoji` can
configure the icon to use for the post.

In addition to
[common task result fields](../processes-v2/flows.html#task-result-data-structure),
the `slack` task returns:

- `ts` -  Timestamp ID of the message that was posted, can be used, in the
  following slack task of posting message, to make the message a reply or in
  `addReaction` action.
- `id` - Channel ID that can be used in subsequent operations.

The optional field from the result object `ts` can be used to create
a thread and reply. Avoid using a reply's `ts` value; use its parent instead.

The optional field `ignoreErrors` can be used to ignore any failures that 
might occur when sending a Slack message. When the value for this field 
is `true`, Concord flow does not throw any exception and fail when the 
slack task fails.

The value defaults to `false` if `ignoreErrors` field is not specified 
in the parameters.

The optional field `replyBroadcast` is used with `ts` and will also post 
the message to the channel. The value defaults to `false` and has no
effect if `ts` is not used. 

### Add Reaction

The Slack task can be used to add a reaction (emoji) to a posted message using
`addReaction` action.

- `action` - action to perform `addReaction`;
- `channelId` - channel ID where the message to add reaction to was posted,
   e.g. `C7HNUMYQ1`;
- `ts` -  timestamp of a posted message to add reaction to. Usually returned
   by the [sendMessage](#send-message) action;
- `reaction` - reaction (emoji) name.

```yaml
flows:
  default:
    - task: slack
      in:
        action: addReaction
        channelId: ${result.id}
        ts: ${result.ts}
        reaction: "thumbup"
        ignoreErrors: true
      out: result

    - if: "${!result.ok}"
      then:
        - log: "Error while adding a reaction: ${result.error}"
```

The `addReaction` action only returns 
[common task result fields](../processes-v2/flows.html#task-result-data-structure).

<a name="slackChannel"/>

## Slack Channel Task

The `slackChannel` task supports creating and archiving channels and groups of the
[Slack](https://slack.com/) messaging platform.

Possible operations are:

- [Create a channel](#create)
- [Archive a channel](#archive)
- [Create a group](#create-group)
- [Archive a group](#archive-group)

The `slackChannel` task uses following input parameters

- `action`: required, the name of the operation to perform `create`, `archive`,
  `createGroup` or `archiveGroup`
- `channelName` the name of the slack channel or group you want to create,
  required for `create` and `createGroup` that you want to create or
- `channelId`: the id of the slack channel that you want to archive, required
  for `archive` and `archiveGroup`.

<a name="create"/>

### Create a Channel

This `slackChannel` task can be used to create a new channel with the `create` action.

```yaml
flows:
  default:
  - task: slackChannel
    in:
      action: create
      channelName: myChannelName
      apiToken: mySlackApiToken
    out: result
  - log: "Channel ID: ${result.slackChannelId}"
```

The identifier of the created channel is available in the returned object in
the `slackChannelId` field.

<a name="archive"/>

### Archive a Channel

This `slackChannel` task can be used to archive an existing channel with the
`archive` action.

```yaml
flows:
  default:
  - task: slackChannel
    in:
      action: archive
      channelId: C7HNUMYQ1
      apiToken: mySlackApiToken
```

The `channelId` can be seen in the URL of the channel  e.g. `C7HNUMYQ1`

<a name="create-group"/>

### Create a Group

This `slackChannel` task can be used to create a group with the `createGroup`
action.

```yaml
flows:
  default:
  - task: slackChannel
    in:
      action: createGroup
      channelName: myChannelName
      apiToken: mySlackApiToken
    out: result
  - log: "Group ID: ${result.slackChannelId}"
```

The identifier of the created group is available in the returned object in
the `slackChannelId` field.

<a name="archive-group"/>

### Archive a Group

This `slackChannel` task can be used to archive an existing group with the
`archiveGroup` action.

```yaml
flows:
  default:
  - task: slackChannel
    in:
      action: archiveGroup
      channelId: C7HNUMYQ1
      apiToken: mySlackApiToken
```
