# Resources

Concord loads the root `concord.yml` first and subsequently looks for the
resource paths under the `resources` section.

If not specified, Concord uses the default `resources` value:

```yaml
resources:
  concord:
    - "glob:concord/{**/,}{*.,}concord.yml"
```

Thus, by default Concord looks for: 
- the root `concord.yml` or `.concord.yml` file;
- `${workDir}/concord/concord.yml`;
- any file with `.concord.yml` extension in the `${workDir}/concord`
directory.

Each element of the `resources.concord` list must be a valid path pattern.
In addition to `glob`, Concord supports `regex` patterns:

```yaml
resources:
  concord:
    - "regex:extraFiles/.*\\.my\\.yml"
```

With the example above Concord loads all files in the `extraFiles` directory
with the `.my.yml` extension. Note that in this case Concord won't look in
the subdirectories.

Multiple patterns can be specified:

```yaml
resources:
  concord:
    - "glob:myConcordFlows/*.concord.yml"
    - "regex:extra/[a-z]\\.concord.yml"
```

See the [FileSystem#getPathMatcher](https://docs.oracle.com/javase/8/docs/api/java/nio/file/FileSystem.html#getPathMatcher-java.lang.String-)
documentation for more details on the `glob` and `regex` syntax.

