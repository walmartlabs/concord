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

5. open [the UI](http://localhost:8001), find the process entry and
open its log. You should see messages like these:
```
13:51:54 [INFO ] My file A: .tmp/file698533367913439655.bin
13:51:54 [INFO ] My file B: .tmp/file4802153809032830974.bin
```
