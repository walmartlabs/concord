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

The tree loads roots and direct children in pages of 100, ordered by segment ID.
Branches start collapsed. Expanding a branch loads its first page; `Load more steps`
loads the next page. Opening a deep link retrieves its ancestor path without loading
its siblings. There is no recursive `Expand all` operation.

Only viewport rows (28 px plus one viewport of overscan on either side, at least five rows) are mounted. Scroll events synchronously update this window to avoid blank areas during fast scrolling. The server refresh window still uses only five extra rows on each side.
Live refresh reads visible pages and visible/selected segment metadata, not the full
process list. Scroll refresh is debounced by 150 ms and reuses data fetched less than
five seconds ago, including for completed processes. Polling and manual refresh bypass this age
check. A refreshed page also updates its nodes: only visible/selected IDs outside
that page need a separate metadata request. Overlapping pending node reads are
coalesced. Selection reuses a complete cached ancestor path, including paths returned
by default/error lookup and search; `/path` is only needed when ancestors are missing.
At most four tree reads run concurrently. Collapsing a branch cancels pending child requests and discards descendant pages; the selected ancestor path is
retained. Loaded pages otherwise stay cached until collapse or leaving the process.

Name search and status filters run on the server across the whole process, with a
250 ms debounce and paginated results plus ancestor context. Error navigation also
works across unloaded branches. Filter totals and descendant counter totals are not
shown from incomplete data: row warning/error counts belong to that segment itself.
Filtering the tree does not filter the selected log viewer.

The right-hand log viewer has its own pagination, independent of tree expansion,
collapse and search. It opens the selected step's own log plus the latest 25
segments of its entire subtree (by creation ID). `Load earlier steps` prepends
another page. Ancestors outside the page preserve hierarchy internally; they do not produce
empty blocks or log requests. Each block heading shows only the step’s own name. Their output appears when they
enter a loaded page or are selected.

Each segment initially shows at most 200 lines. `Load earlier lines` reveals 200
more cached lines, then requests another log page when needed. The selected step's
initial page targets 256 KiB; other pages target 64 KiB, rounded to whole stored
blocks. Up to four log requests run concurrently. Reading history disables automatic
scrolling and preserves the visible line. Live refresh appends new log pages and
new descendant pages, using a forward cursor so bursts cannot skip segments.
Previously loaded history remains available until selection changes. Copy is a
separate full-log request with a 5 MiB limit.
Step details resolve the event-bearing iteration/attempt separately, when opened.

### Tree API

The existing flat segment-list API is unchanged. The tree uses these read-only routes
under `/api/v2/process/{id}/log/tree`, all protected by the existing log-access check:

- `GET /?parentId=&afterId=&limit=100`: direct children, or roots without `parentId`.
  The response contains `items`, `ancestors`, `next`, and `nextId`.
- `GET /search?query=&category=&afterId=&limit=100` searches the whole process.
  Search does not take `parentId`; listing children and searching are separate operations. Categories
  are `FAILED`, `RUNNING`, `WARNINGS`, `OK`. Every segment with status `FAILED`
  remains Failed, including handled failures and unsuccessful retry attempts.
  `errorsOnly=true` and `reverse=true` support global error navigation.
- `GET /nodes?segmentId=1&segmentId=2`: refresh at most 100 segment IDs.
- `GET /{segmentId}/path`: the selected segment and its ancestors.
- `GET /{segmentId}/descendants?limit=25&beforeId=&afterId=`: latest/previous
  descendants in descending ID order, or new descendants in ascending ID order.
  Only one cursor is accepted. `items` are log-bearing page members; `ancestors`
  provide hierarchy inside the selected subtree and do not consume the page limit.
- `GET /{segmentId}/run`: the first iteration / latest attempt used for step details.

Tree entries contain the segment plus read-side `hasChildren`, `hasIterations`,
`retried` and `category` metadata. These are computed, not stored and
not supplied by runners. The original unreleased migration adds a process/parent/ID
index; segment kinds, names, parent IDs and runtime boundaries do not change.

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
