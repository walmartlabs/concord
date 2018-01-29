# Secrets

Example of exporting "secrets" as files.

## Running

1. upload a couple of files as secrets:
```
$ curl -u AD_USERNAME -F name=myFileA -F type=DATA -F storePassword=12345678 -F data=@myFileA.txt 'http://localhost:8001/api/v1/org/Default/secret'
$ curl -u AD_USERNAME -F name=myFileB -F type=DATA -F storePassword=12345678 -F data=@myFileB.txt 'http://localhost:8001/api/v1/org/Default/secret'

{
  "id" : "8febf8ae-0511-11e8-8c13-fa163ec7b48b",
  "result" : "CREATED",
  "password" : "12345678",
  "ok" : true
}
```

The `storePassword` value is the password you must use to decrypt/export the secret later.

2. start the process:
```
$ ./run.sh localhost:8001
{
  "instanceId" : "8ea63d60-10f5-43dd-ba8b-87150fb20182",
  "ok" : true
}
```

5. open [the UI](http://localhost:8080), find the process entry and
open its log. You should see messages like these:
```
12:00:55.817 [INFO ] c.w.c.runner.engine.LoggingTask - Public key file: .tmp/public1856673009465277934.key
12:00:55.821 [INFO ] c.w.c.runner.engine.LoggingTask - Private key file: .tmp/private5358774395817724546.key
12:00:55.832 [INFO ] c.w.c.runner.engine.LoggingTask - Credentials: {password=myPassword, username=myUser}
12:00:55.846 [INFO ] c.w.c.runner.engine.LoggingTask - Plain secret: my horrible secret
```
