# Files

The `files` task provides methods to handle local files within the working directory
of a Concord workflow process.

- [Usage](#usage)
- [Methods](#methods)
  - [Check File Existence](#check-file-existence)
  - [Move a File](#move-a-file)
  - [Construct a Relative Path](#construct-a-relative-path)

## Usage

To be able to use the task in a Concord flow, it must be added as a
[dependency](../processes-v2/configuration.html#dependencies):

```yaml
configuration:
  dependencies:
  - mvn://com.walmartlabs.concord.plugins.basic:file-tasks:{{ site.concord_core_version }}
```

This adds the task to the classpath and allows you to invoke it in any flow.

## Methods

### Check File Existence

The `exists(path)` method returns true when a given path exists within the working
directory.

```yaml
- if: "${files.exists('myFile.txt')}"
  then:
    - log: "found myFile.txt"
```

The `notExists(path)` method returns `true` when a given path _does not_ exist
within the working directory.

```yaml
- if: "${files.notExists('myFile.txt')}"
  then:
    - throw: "Cannot continue without myFile.txt!"
```

### Move a File

The `moveFile(src, dstDir)` method moves a file to a different directory. The returned
value is the new path.

```yaml
- set:
    myFile: "${files.moveFile('myFile.txt', 'another/dir)}"

# prints: "another/dir/myFile.txt"
- log: "${myFile}"
```

### Construct a Relative Path

The `relativize(pathA, pathB)` method returns a relative path from the first
path to the second path.

```yaml
- set:
    relativePath: "${files.relativize('subDirA/myscript', 'subDirB/opts.cfg')}"

- log: "${relativePath}"
# prints: '../../subDirB/opts.cfg'
```
