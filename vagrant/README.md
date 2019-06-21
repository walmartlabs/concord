# Running Concord in Vagrant

## Prerequisites

- Linux or OSX;
- Java 8 and Maven 3.5.0+;
- Vagrant and Virtualbox. Optionally install `vagrant-vbguest`
  plugin to automatically update VBox guest additions:
  `vagrant plugin install vagrant-vbguest`
- Docker, up and running on the host machine. Necessary
  to build and export Concord images;
- (optionally) locally-built Concord images:
  `mvn clean install -Pdocker -DskipTests`

## Usage

Start the VM with `vagrant up`. By default it uses
`walmartlabs/concord-*` images from Docker Hub.
To use another prefix run:
```
IMAGE_PREFIX=myimages vagrant up
```

To use locally-built images run:
```
USE_LOCAL_IMAGES=true vagrant up
```

The API will be available on port `18001`:
```
$ curl http://localhost:18001/api/v1/server/ping
{
  "ok" : true
}
```

The Console will be available on port `18080`.
The default user is `myuser` with password `q1`.

The examples can be started using `localhost:18001` as the API
endpoint:
```
$ cd ../examples/hello_world
$ ./run.sh localhost:18001
Username: myuser
Enter host password for user 'myuser': q1
{
  "instanceId" : "...",
  "ok" : true
}
```

The VM can be stopped with `vagrant halt` or suspended
with `vagrant suspend`.

## Updating the Local Images

1. build new Concord images;
2. run `USE_LOCAL_IMAGES=true vagrant provision`.

## TODO

- use persistent volumes for the database.
