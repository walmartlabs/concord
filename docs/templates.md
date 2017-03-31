# Process templates

Process templates are archives of configuration, process definitions,
dependencies, and any supporting files that are necessary to execute a
process.

## Uploading a Template to Concord Server

This example assumes that your Concord Server is listening on
localhost port 8001.  It will associate the template archive file with
a template named "myTemplate."

```
curl -v \
-H "Content-Type: application/octet-stream" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
--data-binary @template.zip \
http://localhost:8001/api/v1/template?name=myTemplate
```

The Concord Server will respond to a successful template upload with
the following JSON response.

```json
{
  "ok": true
}
```

## Concord Template Format

A ZIP archive of the following structure:

- `_main.js` - a request data javascript file (see below);

- `processes/*.yml` - process definitions. They will be added to the
  resulting payload as is;

- `lib/*.jar` - dependencies.

## Request data pre-processing

A `_main.js` file can be used to generate or modify request data
(arguments) for a process.  The result of evaluation must be a
JSON-compatible object.

Original request data is available by using the `_input` variable.

### Template Arguments Example

Template's **_main.js**:

```javascript
({
    entryPoint: "main",
    arguments: {
        greeting: "Hello, " + _input.myName
    }
})
```

Original **_main.json**, supplied by a user:

```json
{
  "myName": "anonymous"
}
```

The resulting JSON data:

```json
{
  "entryPoint": "main",
  "arguments": {
    "greeting": "Hello, anonymous"
  }
}
```