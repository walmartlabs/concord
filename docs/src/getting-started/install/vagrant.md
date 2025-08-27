# {{ page.title }}

Requires Vagrant 2.2+.

To start Concord using [Vagrant](https://www.vagrantup.com/):

```bash
$ git clone https://github.com/walmartlabs/concord.git
$ cd concord/vagrant
$ vagrant up
```

It starts Concord using the latest available Docker images.
OpenLDAP running in a container will be used for authentication.

The `vagrant up` command might take a while, typically ~5 minutes, depending
on the internet connection.

Refer to the [README.md](https://github.com/walmartlabs/concord/blob/master/vagrant/README.md)
file for more details.
