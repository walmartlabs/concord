# Concord UI

## Prerequisites

- Node 24 LTS, available in `$PATH`;
- Java 25, available in `$PATH`. Necessary only to build the package.

## Dependencies

To install the necessary dependencies for the first time:
```bash
$ npm ci --legacy-peer-deps
```

To update `package-lock.json` run
```bash
$ ../mvnw clean package # only for the first run
$ ../mvnw com.github.eirslett:frontend-maven-plugin:npm -Darguments=install
```

### Third-party notices

License notices for the retained copied UI components and helpers are kept in
[src/licenses/semantic-ui](src/licenses/semantic-ui).
The Maven build includes these notices under `META-INF/licenses/semantic-ui/`
in the console JAR.

### Local UI components

The checked component implementations live in `src/components/ui`; Fomantic
component styles are imported in `src/styles/fomantic.css`. Keep styles used by
plain application markup too, including `embed.css` for custom process-card forms.

Run `npm run build:check` and `npm test -- --run` when changing component contracts.
The UI regression suites cover custom component roots and labels, overlay ordering
and subscription cleanup, dropdown filtering/additions, and calendar formatting/locales.

Component contracts are maintained in TypeScript, without duplicate runtime
PropTypes catalogs. Keep explicit `handledProps` lists and functional defaults:
prop forwarding and default behavior depend on them. Targeted development
diagnostics and functional environment checks remain independent of validation.

`Input`, `Button`, and `TextArea` retain class-instance refs for compatibility with
DOM-resolving overlay wrappers. Their public `InputHandle`, `ButtonHandle`, and
`TextAreaHandle` types expose `focus()`; `InputHandle` also exposes `select()`.
Keep event-stack callback identities stable and unsubscribe from the original DOM
target, not a ref that may already have been cleared during unmount.

## Running in Dev Mode

In the dev mode the UI is served by running `npm start`.

First time:
```bash
$ npm ci --legacy-peer-deps
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
