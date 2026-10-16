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

## Process Log Tree

The log-tree view mounts only the viewport's 28 px rows plus five overscan rows
on each side. Filtering, keyboard navigation, error navigation, and selection use
the complete logical tree, including rows outside the mounted window. Selection
scrolls only the tree's vertical viewport; horizontal scrolling remains independent.

Activity, tree, and row memo boundaries isolate unrelated parent updates. Fresh
log snapshots still refresh mounted statuses, counters, and running-step timing.


### Tree view v2

The separate `/process/:instanceId/log-tree-v2` tab uses the same polling, tree model,
filters, error navigation and virtualized viewport as Log Tree. Structural groups
are collapsible headings with thin guides. Their **Container logs** row selects the
existing segment and displays only its own messages; no additional segments or
renaming are involved. Named step rows keep their existing log viewer behavior.
Heading and content rows both occupy 28 px, so large loops remain virtualized.
Links copied from this view retain `/log-tree-v2`.

#### Backend API follow-up

The new view works with the existing segment-list and segment-log endpoints.
The runner now records LOOP, RETRY and ITERATION_RETRY in the existing `kind` field,
so group headings exist before their first child, including for empty loops and
failed setup. The UI does not infer group types from children. Older STEP/ITERATION
segments retain their hierarchy and logs as ordinary rows. Names and parent IDs
are unchanged. An ITERATION_RETRY remains an iteration and retains its item index.

One optional API addition remains: **own-log availability in the list response**,
for example `hasOwnLogs`, counting only messages belonging to that segment. Currently
every group has a Container logs row, including empty ones. This field would allow
hiding empty entries without one extra request per container. Decide whether to
reserve the row while running to avoid shifting the tree as logs arrive.

Optionally return a planned iteration count when known, separately from the count
of created children. The current caption reports only observed iterations/attempts,
not a known total. No new log endpoint, parent relation, or mutable segment name is
needed. Existing own `errors`/`warnings` counters and status fields remain in use.
