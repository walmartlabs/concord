# Concord Console

## Development mode

```
npm install
npm start
```

### Type checking

The project uses [Facebook's Flow](https://flowtype.org/).

1. install flow:
   ```
   npm install -g flow-bin flow-typed
   ```
2. install type definitions (in the module's directory):
   ```
   flow-typed install
   ```

There is a helper script to run `flow server` using the modules's
directory as a root directory: `./flow-server.sh`.

## Production

```
mvn clean install
```

Production build can be served locally using the following command:

```
npm run server
```

It will start a HTTP server on port 3000, serving static resources from the `./build` directory and
proxying all requests to `/api` and `/logs` to the backend server at `localhost:8001`.
See [the server script](./scripts/server.js) for more details.
