# Concord UI

## Prerequisites

- Node 24 LTS in `$PATH`.
- Java 25 in `$PATH`. Java is required only to build the package.

## Dependencies

Install the required dependencies for the first time:
```bash
$ npm ci
```

To update `package-lock.json`, run:
```bash
$ ../mvnw clean package # only for the first run
$ ../mvnw com.github.eirslett:frontend-maven-plugin:npm -Darguments=install
```

## Running in Dev Mode

In development mode, run `npm start` to serve the UI.

For the first run, install dependencies and start the development server:
```bash
$ npm ci
$ npm run dev
```

Open http://localhost:3000.

Skip the `ci` step on later runs.

Development mode has these limitations:
- File downloads, such as raw logs, do not work.
- [Custom forms](https://concord.walmartlabs.com/docs/getting-started/forms.html#custom) do not work.

To use these features, run the UI in production mode. See below.

## Running in Production Mode

In production mode, Concord Server serves the UI from the JAR file that the
concord-console2 [build](./pom.xml) creates.

When running locally, it is available at http://localhost:8001.

## Configuration

Set the path to the `cfg.js` file when you start
[the Server](../server/dist):

```
CONSOLE_CFG_FILE=/path/to/cfg.js
```

Or, set the path in `concord-server.conf`:

```
concord-server {
  console {
     cfgFile = "/path/to/cfg.js"
  }
}
```

Use [./public/cfg.js](./public/cfg.js) as an example.
