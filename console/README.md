# Concord Console

### Development mode

```
npm install
npm start
```

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
