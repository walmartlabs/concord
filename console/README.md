# Concord Console

This is the frontend webapp for Concord.

## Development workflow

```
npm install
npm start
```

It will start a HTTP server on port 3000, with API request proxy to 8001.  Proxy is configured in package.json

The easiest way to test custom (branded) forms is to use the console's docker image:
```
./docker-images/console/local.sh
```

The UI will be available on the port 8080.

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

### Testing

Testing is done using the following tools: 
* <a href="https://facebook.github.io/jest/docs/en/getting-started.html">Jest</a> by facebook, for assertions and snapshot tests
* <a href="http://airbnb.io/enzyme/">Enzyme</a> by AirBnB, for React Component rendering and traversal

Start testing by running the following command in a separate terminal window.
```
npm run test
```
The tests will watch your code for changes and will update results as your code and assertions change.

### Redux Store
You might find it useful to install the <a href="https://github.com/zalmoxisus/redux-devtools-extension">redux development tools chrome extension</a>
It will visualize the current state of the store when you have the application rendering in chrome.

<b>NOTE:</b> The concord Project is already configured to work with the extension.

### Live Isolated Component Development

[React Storybook](https://github.com/storybooks/storybook)
Storybook is a live sandbox editor that allows you to completely isolate your components from one another.

Run in development mode with the following command:
```
npm run storybook
```
Build a project snapshot with 
```
npm run build-storybook
```

## Production

```
mvn clean install
```

Production build can be served locally using the following command:

```
npm run server
```

It will start a HTTP server on port 8080, serving static resources from the `./build` directory and
proxying all requests to `/api` and `/logs` to the backend server at `localhost:8001`.
See [the server script](./scripts/server.js) for more details.
