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

The tree loads all segment metadata using the existing
`GET /api/v2/process/{id}/log/segment?offset=0&limit=-1` endpoint. Parent links,
name search, status filters, error navigation and step-detail selection are resolved
on the client. There is no separate tree API or segment pagination.

Live polling refreshes the full metadata list, including existing segment statuses.
Selection and expanded branches are retained across refreshes. Collapsing a branch
only hides its rows; metadata stays in memory until leaving the process.
Only viewport rows plus one viewport of overscan on each side (at least five rows)
are mounted. Filtering the tree does not filter the selected log viewer.
Failed includes every segment with status FAILED, including unsuccessful attempts.

The right-hand viewer opens the selected step's own log plus the latest 100
nested segments by creation ID. `Load earlier steps` reveals another 100 from
metadata already in memory, independently of expansion and filtering on the left.
Skipped ancestors do not produce empty blocks or log requests. Each block heading
shows only the step's own name.

Log text is fetched separately, on demand. Each segment initially shows at most
200 lines. `Load earlier lines` reveals 200 more cached lines, then requests another
log page when needed. The selected step's initial page targets 256 KiB; other pages
target 64 KiB, rounded to whole stored blocks. Up to four log requests run concurrently.
Reading history temporarily pauses automatic scrolling and preserves the visible line.
The pause ends when selecting another step or explicitly enabling Auto-scroll. Only manual
changes to the Auto-scroll checkbox update the saved preference.
Live refresh appends new log pages and includes newly discovered descendants.
Previously displayed history remains available until selection changes.
Copy is a separate full-log request with a 5 MiB limit.

### Log pages

`GET /api/v2/process/{id}/log/segment/{segmentId}/data/page?limit=65536&before=&after=`
returns `{text, before, after, hasPrevious, hasNext}`. Without a cursor it reads the
tail. `before` reads older blocks; `after` reads newer blocks. Cursors are the
immutable block boundaries returned by this API and are scoped to one segment.
Pages do not overlap: the UI prepends or appends text, without byte-range merging.
A limit of 1–1,048,576 bytes is a target rounded to whole blocks, not a hard cap.
Empty responses retain the cursor, even when a concurrent append changes the size.
The existing binary data route remains available for downloads and Copy.

### Segment names

Log Tree uses the same selectable row for every segment, including loops, retries,
error handlers and parallel blocks. Selecting a node opens its log with the existing
child-segment links; there are no separate Container logs rows or group headings.

Repeated steps retain their original name templates. `${...}` expressions are gray
in the tree, selected heading and child links. The renderer only styles text and
never evaluates it. Plain loops have ITERATION children with evaluated names. With retry, ATTEMPT children
replace the iteration containers and evaluate the name in the attempt context.
Segments expose separate zero-based `iterationIndex` and `attemptIndex` fields;
attempts inside a loop carry both, plain iterations carry only `iterationIndex`,
and retries outside a loop carry only `attemptIndex`. Names are
never changed after creation. The API uses STEP, ITERATION, ATTEMPT, ERROR and PARALLEL;
there are no separate loop/retry group kinds.


The right-hand log viewer displays all levels with ordinary byte-range pagination.
Level filtering is deferred to a separate change; the server does not scan log text
to find warnings or errors. Cursors are scoped to the segment.
Tree status filters remain available and operate on segment metadata.
