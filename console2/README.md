# Concord UI

Uses https://github.com/facebook/create-react-app/ as the boilerplate.    

## Prerequisites

- Node 10 or greater, available in `$PATH`;
- Java 8, available in `$PATH`. Necessary only to build the package.

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

First time:
```bash
$ npm ci
$ npm start
```

The browser should automatically open on http://localhost:3000
(the initial load might take a while, especially on slower machines).

The `ci` step can be skipped for subsequent runs.

The dev mode has the following limitations:
- file download (e.g. downloading raw logs) doesn't work;
- [custom forms](https://concord.walmartlabs.com/docs/getting-started/forms.html#custom) don't work.

In order to use those features, you need to run the UI in production
mode (see below).

## Building and Running in Production Mode

```bash
../mvnw clean install
```

Specify the path to the `build` directory when you start
[the Server](../server/dist):
```
BASE_RESOURCE_PATH=/path/to/repository/concord/console2/build
```

The UI will be accessible on http://localhost:8080

**Note:** avoid using symlinks in BASE_RESOURCE_PATH.

## Custom Server URL

The `proxy` property in the [package.json](./package.json) file is used to
proxy API requests to the server. It can be used to point the UI in dev mode to
another server.