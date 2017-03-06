# Process templates

## Uploading

```
curl -v \
-H "Content-Type: application/octet-stream" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
--data-binary @template.zip \
http://localhost:8001/api/v1/template?name=myTemplate
```

```json
{
  "ok": true
}
```

## Template format

A ZIP archive of the following structure:
- `_main.js` - a request data javascript file (see below);
- `processes/*.yml` - process definitions. They will be added to the resulting payload as is;
- `lib/*.jar` - dependencies.

## Request data pre-processing

A `_main.js` file can be used to generate or modify request data (arguments) for a process.
The result of evaluation must be a JSON-compatible object.

Original request data is available by using the `_input` variable.

### Example

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