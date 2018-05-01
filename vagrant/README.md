# Running Concord in Vagrant

## Prerequisites

- Linux or OSX;
- Java 8 and Maven 3.5.0+;
- Vagrant and Virtualbox. Optionally install `vagrant-vbguest`
  plugin to automatically update VBox guest additions:
  `vagrant plugin install vagrant-vbguest`
- Docker, up and running on the host machine. Necessary
  to build and export Concord images;
- locally-built Concord images: `mvn clean install -Pdocker`

## Usage

Start the VM with `vagrant up`.

The API will be available on port `18001`:
```
$ curl http://localhost:18001/api/v1/server/ping
{
  "ok" : true
}
```

The Console will be available on port `18080`.

The default user is `myuser` with password `q1`.

The VM can be stopped with `vagrant halt` or suspended
with `vagrant suspend`.

## Updating the images

1. build new Concord images;
2. run `vagrant provision`.

## TODO

- use persistent volumes for the database.
