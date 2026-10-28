({
    entryPoint: "main",
    runtime: "concord-v2",
    dependencies: [
        "mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:${project.version}"
    ],
    arguments: {
        ansibleParams: _input
    }
});
