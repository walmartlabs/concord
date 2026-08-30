# Imports

Resources such as flows, forms and other workflow files can be shared between
Concord projects by using `imports`.

How it works:

- when the process is submitted, Concord reads the root `concord.yml` file
  and looks for the `imports` declaration;
- all imports are processed in the order of their declaration;
- `git` repositories are cloned and their `path` directories are copied into the
  `dest` directory of the process working directory;
- `mvn` artifacts are downloaded and extracted into the `dest` directory;
- any existing files in target directories are overwritten;
- the processes continues. Any imported resources placed into `concord`,
  `flows`, `profiles` and `forms` directories will be loaded as usual.

For example:

```yaml
imports:
  - git:
      url: "https://github.com/walmartlabs/concord.git"
      path: "examples/hello_world"

configuration:
  arguments:
    name: "you"
```

Running the above example produces a `Hello, you!` log message.

The full syntax for imports is:

```yaml
imports:
  - type:
      options
  - type:
      options
```

Note, that `imports` is a top-level objects, similar to `configuration`.
In addition, only the main YAML file's, the root `concord.yml`, `imports` are
allowed.

Types of imports and their parameters:

- `git` - imports remote git repositories:
  - `url` - URL of the repository, either `http(s)` or `git@`;
  - `name` - the organization and repository names, e.g. `walmartlabs/concord`.
  Automatically expanded into the full URL based on the server's configuration.
  Mutually exclusive with `url`;
  - `version` - (optional) branch, tag or a commit ID to use. Default `master`;
  - `path` - (optional) path in the repository to use as the source directory;
  - `dest` - (optional) path in the process' working directory to use as the
  destination directory. Defaults to the process workspace `./concord/`;
  - `exclude` - (optional) list of regular expression patterns to exclude some files when importing;
  - `secret` - reference to `KEY_PAIR` or a `USERNAME_PASSWORD` secret. Must be
  a non-password protected secret;
- `mvn` - imports a Maven artifact:
  - `url` - the Artifact's URL, in the format of `mvn://groupId:artifactId:version`.
    Only JAR and ZIP archives are supported;
  - `dest` - (optional) path in the process' working directory to use as the
  destination directory. Default `./concord/`.

The `secret` reference has the following syntax:
- `org` - (optional) name of the secret's org. Uses the process's organization
if not specified;
- `name` - name of the secret;
- `password` - (optional) password for password-protected secrets. Accepts
literal values only, expressions are not supported. 

An example of a `git` import using custom authentication:

```yaml
imports:
  - git:
      url: "https://github.com/me/my_private_repo.git"
      secret:
        name: "my_secret_key"
```
