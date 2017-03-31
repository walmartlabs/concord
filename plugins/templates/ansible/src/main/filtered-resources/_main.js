({
    entryPoint: "main",
    dependencies: [
        "concord:///ansible-tasks-${project.version}.jar"
    ],
    arguments: {
        ansibleParams: _input
    }
});
