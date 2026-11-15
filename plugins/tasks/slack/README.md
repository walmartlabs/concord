# slack-tasks

Send messages and manage channels with this plugin.

## Required OAuth Permissions

The `slack` task requires these OAuth scopes:

- [`chat:write`](https://api.slack.com/scopes/chat:write) - Allows the task to send messages to channels.
- [`chat:write.customize`](https://api.slack.com/scopes/chat:write.customize) - Allows the task to customize the message `username` and `iconEmoji`.
- [`reactions:write`](https://docs.slack.dev/reference/scopes/reactions.write) - Allows the task to add reactions to messages with the `addReaction` action.
- [`chat:write.public`](https://api.slack.com/scopes/chat:write.public) - Optional. Allows the task to send messages to public channels without membership.

The `slackChannel` task requires these OAuth scopes:
- [`channels:manage`](https://api.slack.com/scopes/channels:manage) - Allows the task to create and archive public channels.
- [`groups:write`](https://api.slack.com/scopes/groups:write) - Allows the task to create and archive private channels.
