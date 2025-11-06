# slack-tasks

Send messages and manage channels.

## Required OAuth Permissions

Required `slack` task OAuth scopes:

- [`chat:write`](https://api.slack.com/scopes/chat:write) - 
    For sending messages to channels.
- [`chat:write.customize`](https://api.slack.com/scopes/chat:write.customize) -
    For customizing message `username` and `iconEmoji`.
- [`reactions:write`](https://docs.slack.dev/reference/scopes/reactions.write) -
    For adding reactions to messages using the `addReaction` action.
- [`chat:write.public`](https://api.slack.com/scopes/chat:write.public) -
    Optional, for sending messages to public channels without membership.

Required `slackChannel` task OAuth scopes:

- [`channels:manage`](https://api.slack.com/scopes/channels:manage) -
    For creating and archiving public channels.
- [`groups:write`](https://api.slack.com/scopes/groups:write) -
    For creating and archiving private channels.
