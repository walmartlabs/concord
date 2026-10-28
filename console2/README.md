# Concord UI

## Prerequisites

- Node 24 LTS, available in `$PATH`;
- Java 25, available in `$PATH`. Necessary only to build the package.

## Dependencies

To install the necessary dependencies for the first time:
```bash
$ npm ci
```

To update `package-lock.json` run
```bash
$ ../mvnw clean package # only for the first run
$ ../mvnw com.github.eirslett:frontend-maven-plugin:npm -Darguments=install
```

## Running in Dev Mode

In the dev mode the UI is served by running `npm start`.

First time:
```bash
$ npm ci
$ npm run dev
```

Open http://localhost:3000.

The `ci` step can be skipped for subsequent runs.

The dev mode has the following limitations:
- file download (e.g. downloading raw logs) doesn't work;
- [custom forms](https://concord.walmartlabs.com/docs/getting-started/forms.html#custom) don't work.

In order to use those features, you need to run the UI in production
mode (see below).

## Running in Production Mode

In the production mode the UI is served by concord-server from the JAR file
created during concord-console2 [build](./pom.xml).

When running locally, it is available at http://localhost:8001.

### Checking identity and source management

With a server containing two accounts with the same name but different identities,
open the built Console and search for them in a team or owner selector. Check that
the provider, domain, and UUID distinguish the results. Add one account to a team,
remove that same UUID, and change an owner to the other account; confirm the
resulting membership and owner in the UI. Check that direct and source-derived
team memberships display their provenance and that source-qualified group
management keeps identically named groups from different sources separate.
Capture screenshots of the relevant screens and close the browser tab afterward.

The team's Groups tab lists discovered authorization sources, including inactive sources,
and defaults to LDAP when present. Saving replaces mappings only for the selected source.

## Configuration

Specify the path to the `cfg.js` file when you start
[the Server](../server/dist):

```
CONSOLE_CFG_FILE=/path/to/cfg.js
```

or using concord-server.conf:

```
concord-server {
  console {
     cfgFile = "/path/to/cfg.js"
  }
}
```

Use [./public/cfg.js](./public/cfg.js) as an example.
