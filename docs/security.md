# Security

## Permissions

### Core

| Wildcard                  | Description                                                    | Examples                               |
|---------------------------|----------------------------------------------------------------|----------------------------------------|
| `apikey:create`           | Create a new API key.                                          |                                        |
| `apikey:delete`           | Delete any API key.                                            |                                        |
| `process:start:$project`  | Starting a process from the specified `project`.               | `process:start:*`, `process:start:ABC` |
| `project:update:$project` | Update (modify) a project. E.g. add a repository to a project. | `project:update:ABC`                   |
| `project:create`          | Create a new project.                                          |                                        |
| `repository:create`       | Create a new repository.                                       |                                        |
| `repository:delete:*`     | Delete specific repository.                                    |                                        |
| `repository:update:*`     | Update specific repository.                                    |                                        |
| `secret:create`           | Create a new secret.                                           |                                        |
| `secret:read:%s`          | Read an existing secret's data.                                |                                        |
| `secret:delete:%s`        | Delete specific secret.                                        |                                        |
| `template:use:$template`  | Use a specific template.                                       | `template:use:ansible`                 |
| `template:create`         | Create a new project template.                                 |                                        |
| `template:delete:*`       | Delete specific template.                                      |                                        |
| `template:update:*`       | Update specific template.                                      |                                        |
| `user:create`             | Create a new user.                                             |                                        |
| `user:delete`             | Delete any user.                                               |                                        |
| `user:update`             | Update any user.                                               |                                        |

### Extension: ansible-inventory

| Wildcard                      | Description                                                    | Examples                    |
|-------------------------------|----------------------------------------------------------------|-----------------------------|
| `inventory:create`            | Create a new inventory file.                                   |                             |
| `inventory:use:$inventory`    | Use specified inventory file (e.g. in a process).              | `inventory:use:myInventory` |
