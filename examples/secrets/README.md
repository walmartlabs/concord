# Secrets

Example of using "secrets" in processes.

## Running

1. create a new SSH key pair:
```
$ curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -F storePassword=12345678 -F name=myKey -F type=KEY_PAIR 'http://localhost:8001/api/v1/org/Default/secret'
{
  "name" : "myKey",
  "publicKey" : "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCmV6+q6Uh8j8GYl0nzcwTGpjBwY1Dvv3QIAfmdwC8N6HredMl5hV3RCtpplYR7aItTorWVUYF1MMmXYKr6tjgU9hha2N2NogRjgPWzSVuR8GVa7CF155NB4nlUxt5cGidLj5Uwmy/uQm4Mni5pg/kZMGyIf+gMmcuQXDG3TOHwmJ48HOrpqxkUKaft3SYYOy7F8TjWFnmyXNlMCskSEJd5XdLDhyuDhDEXGpDSsT1brsq0WRXtFyBDjjNeYfI4J9jyOCpAXzbDCt7eYoYK+kod/b6RV8nbaxWALx2fwJS0bDhV3a9chwEyat24Ml66Z5LfCabCE7SGpFhTas56xYkH concord-server",
  "exportPassword" : "12345678",
  "ok" : true
}
```

The `storePassword` value is the password you must use to decrypt/export the secret later.

2. create a new username/password pair:
```
$ curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -F username=myUser -F password=myPassword -F storePassword=12345678 -F name=myCreds -F type=USERNAME_PASSWORD 'http://localhost:8001/api/v1/org/Default/secret'
{
  "exportPassword" : "12345678",
  "ok" : true
}
```

For the sake of the example, the same `storePassword` value is used.

3. create a plain value secret:
```
$ curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -F secret='my horrible secret' -F storePassword=12345678 -F name=myValue -F type=DATA 'http://localhost:8001/api/v1/org/Default/secret'
{
  "exportPassword" : "12345678",
  "ok" : true
}
```

4. start the process:
```
$ ./run.sh localhost:8001
{
  "instanceId" : "8ea63d60-10f5-43dd-ba8b-87150fb20182",
  "ok" : true
}
```

5. open [the UI](http://localhost:8080), find the process entry and
open its log. You should see messages like this:
```
12:00:55.817 [INFO ] c.w.c.runner.engine.LoggingTask - Public key file: .tmp/public1856673009465277934.key
12:00:55.821 [INFO ] c.w.c.runner.engine.LoggingTask - Private key file: .tmp/private5358774395817724546.key
12:00:55.832 [INFO ] c.w.c.runner.engine.LoggingTask - Credentials: {password=myPassword, username=myUser}
12:00:55.846 [INFO ] c.w.c.runner.engine.LoggingTask - Plain secret: my horrible secret
```
