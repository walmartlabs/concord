# slack-tasks

Send messages and manage channels.

## Required OAuth Permissions

Required `slack` task OAuth scopes:

- [`chat:write`](https://api.slack.com/scopes/chat:write) - 
    For sending messages to channels.
- [`chat:write.customize`](https://api.slack.com/scopes/chat:write.customize) -
    For customizing message `username` and `iconEmoji`.
- [`chat:write.public`](https://api.slack.com/scopes/chat:write.public) -
    Optional, for sending messages to public channels without membership.

Required `slackChannel` task OAuth scopes:

- [`groups:write`](https://api.slack.com/scopes/groups:write) -
    For creating and archiving private channels.
