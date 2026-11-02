# Running Concord in Vagrant

## Prerequisites

- Linux or macOS.
- Java 8 and Maven 3.5.0 or later.
- Vagrant and VirtualBox. You can also install the `vagrant-vbguest` plugin to
  update VirtualBox guest additions automatically:
  `vagrant plugin install vagrant-vbguest`
- Docker running on the host machine. Docker is required to build and export Concord images.
- Optional: Locally built Concord images:
  `mvn clean install -Pdocker -DskipTests`

## Usage

Start the VM with `vagrant up`. By default, it uses `walmartlabs/concord-*`
images from Docker Hub.
To use a different image prefix, run:
```
IMAGE_PREFIX=myimages vagrant up
```

To use locally built images, run:
```
USE_LOCAL_IMAGES=true vagrant up
```

The API is available on port `18001`:
```
$ curl http://localhost:18001/api/v1/server/ping
{
  "ok" : true
}
```

The Console is also available on port `18001`: http://localhost:18001/
The default user name is `myuser`. The password is `q1`.

To run the examples, use `localhost:18001` as the API endpoint:
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

Stop the VM with `vagrant halt`. Suspend it with `vagrant suspend`.

## Updating the Local Images

1. Build new Concord images.
2. Run `USE_LOCAL_IMAGES=true vagrant provision`.

## TODO

- use persistent volumes for the database.
