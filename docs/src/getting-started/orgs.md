# Organizations and Teams

Concord implements role-based access control using Organizations and Teams.

- [Organizations](#organizations)
- [Teams](#teams)
- [Public and Private Resources](#public-and-private-resources)

## Organizations

Organizations own resources such as projects, secrets, inventories and
processes. Organizations contain one or more [teams](#teams).

Organizations are created by Concord administrators using [the REST API](../api/org.md).

## Teams

Teams are a part of an Organization and represent groups of users. Users in
teams can have different **team roles**:
- `MEMBER` - a regular team member, has access to the team's resources, but
cannot invite other users to the team or manage organizations;
- `MAINTAINER` - has the same permissions as a `MEMBER` and can manage users
in the team;
- `OWNER` - has the same permissions as a `MAINTAINER`, but, in addition, can
manage the team's Organization.

Teams have different **access levels** to the Organization's resources:
- `READER` - can use the resource;
- `WRITER` - can use and modify the resource;
- `OWNER` - can use, modify or remove the resource.
 
## Public and Private Resources

Resources such as project, secrets and inventories can have different
**visibility**:
- `PUBLIC` - any Concord user can access and use the resource. For example,
a public project can be used by anyone to start a new process.
- `PRIVATE` - only [teams](#teams) that have an appropriate **access level**
can use the resource.

If a public project references another resource, for example, a secret used
to retrieve the project's repository, the references resource must be `PUBLIC`
as well or have an appropriate access level set up.