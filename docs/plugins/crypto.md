# Crypto task

This task provides a method to decrypt values encrypted with a project's key.

## Usage

### Encrypting a value

Encrypting `my secret value` using the key of `myProject` project.

```
curl -H "Content-Type: application/json" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-d '{ "value": "my secret value" }' \
http://localhost:8001/api/v1/project/myProject/encrypt
```

The result will look like this:

```json
{
  "data" : "4d1+ruCra6CLBboT7Wx5mw==",
  "ok" : true
}
```

The value of `data` field must be used as-is as a process variable.
It can be added to `.concord.yml`, project's configuration or to
request JSON.

### Decrypting a value

To decrypt the previously encrypted value:

```yaml
- ${crypto.decryptString("4d1+ruCra6CLBboT7Wx5mw==")}
```

Alternatively, the encrypted value can be passed as a variable:

```yaml
- ${crypto.decryptString(mySecret)}
```
