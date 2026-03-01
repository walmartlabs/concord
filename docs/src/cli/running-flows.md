# Running Flows

- [Overview](#overview)
- [Secrets](#secrets)
- [Dependencies](#dependencies)
  - [Configuring Extra Repositories](#configuring-extra-repositories)
- [Imports](#imports)

## Overview

**Note:** this feature is still under active development. All features
described here are subject to change.

**Note:** this feature supports only [`concord-v2` flows](../processes-v2/index.md).
The CLI tool forces the `runtime` parameter value to `concord-v2`.

The CLI tool can run Concord flows locally:

```yaml
# concord.yml
flows:
  default:
    - log: "Hello!"
```

```
$ concord run
Starting...
21:23:45.951 [main] Hello!
...done!
```

By default, `concord run` copies all files in the current directory into
a `$PWD/target` directory -- similarly to Maven.

The `concord run` command doesn't use a Concord Server, the flow execution is
purely local. However, if the flow uses external resources (such as
`dependencies` or `imports`) a working network connection might be required.

Supported features:
- all regular [flow](../processes-v2/flows.md) elements;
- [dependencies](#dependencies);
- [imports](#imports);
- [secrets]({{ site.concord_plugins_v2_docs }}/crypto.md). See [below](#secrets) for
more details.

Features that are currently *not* supported:
- [forms](../getting-started/forms.md);
- [profiles](../processes-v2/profiles.md);
- password-protected secrets.

## Secrets

By default, Concord CLI uses a local file-based storage to access
[secrets]({{ site.concord_plugins_v2_docs }}/crypto.md) used in flows.

**Note:** currently, all secret values stored without encryption. Providing
a password in the `crypto` task arguments makes no effect.

### Secret Store Directory

Concord CLI resolves Secret data in `$HOME/.concord/secrets` by default. This can
be customized by providing the `--secret-dir` flag.

```shell
$ concord run --secret-dir="$HOME/.my_secrets" ...
```

### String Secrets

```yaml
# concord.yml
flows:
  default:
    - log: "${crypto.exportAsString('myOrg', 'mySecretString', null)}"
```

Concord CLI looks for a `$HOME/.concord/secrets/myOrg/mySecretString` file
and returns its content.

### Key Pair Secrets

```yaml
# concord.yml
flows:
  default:
    - set:
        keyPair: "${crypto.exportKeyAsFile('myOrg', 'myKeyPair', null)}"
```

For key pair secrets, Concord CLI looks for two files:

- `$HOME/.concord/secrets/myOrg/myKeyPair` (private key)
- `$HOME/.concord/secrets/myOrg/myKeyPair.pub` (public key)

### Username/Password Secrets

Concord CLI looks for a single file matching the Secret name, in a
directory name matching the Secret's organization within the
[secret store directory](#secret-store-directory). Given the following crypto
call:

```yaml
#concord.yml
flows:
  default:
    - log: "${crypto.exportCredentials('myOrg', 'myCredentials', null)}"
```

When executed, Concord CLI loads the data from `$HOME/.concord/secrets/myOrg/myCredentials`.

```json
{
  "username": "the_actual_username",
  "password": "the_actual_password"
}
```

### File Secrets

Concord CLI copies a file matching the Secret name, in a directory name matching
the Secret's organization within the [secret store directory](#secret-store-directory).
Given the following crypto call:

```yaml
#concord.yml
flows:
  default:
    - log: "${crypto.exportAsFile('myOrg', 'myFile', null)}"
```

When executed, Concord CLI copies the file from `$HOME/.concord/secrets/myOrg/myFile`
to a random temporary file.

### Project-Encrypted Strings

Concord CLI also supports the `crypto.decryptString` method, but instead of
decrypting the provided string, the string is used as a key to look up
the actual value in a "vault" file.

The default value file is stored in the `$HOME/.concord/vaults/default`
directory and has very simple key-value format:

```
key = value
```

Let's take this flow as an example:

```yaml
flows:
  default:
    - log: "${crypto.decryptString('ZXhhbXBsZQ==')}"
```

When executed, it looks for the `ZXhhbXBsZQ==` key in the vault file and
returns the associated value.

```
$ cat $HOME/.concord/vaults/default
ZXhhbXBsZQ\=\= = hello!

$ concord run
Starting...
21:52:07.221 [main] hello!
...done!
```

## Dependencies

Concord CLI supports flow [dependencies](../processes-v2/configuration.md#dependencies).

By default, dependencies cached in `$HOME/.concord/depsCache/`.

For Maven dependencies Concord CLI uses [Maven Central](https://repo.maven.apache.org/maven2/)
repository by default.

### Configuring Extra Repositories

Create a maven repository configuration file for concord in `$HOME/.concord/mvn.json`.
Set the contents to an object with a `repositories` attribute containing a list
of maven repository definitions.

```json
{
  "repositories": [
    {
      "id": "host",
      "url": "file:///home/MY_USER_ID/.m2/repository"
    },
    {
      "id": "internal",
      "url": "https://my.nexus.repo/repository/custom_maven_repo"
    },
    {
      "id": "central",
      "url": "https://repo.maven.apache.org/maven2/"
    }
  ]
}
```

## Imports

Concord CLI supports flow [imports](../processes-v2/imports.md).

For example:
```yaml
# concord.yml
imports:
  - git:
      url: "https://github.com/walmartlabs/concord.git"
      path: "examples/hello_world"
```

When executed it produces:

```
$ concord run
Starting...
21:58:37.918 [main] Hello, Concord!
...done!
```

By default, Concord CLI stores a local cache of `git` imports in
`$HOME/.concord/repoCache/$URL`.
