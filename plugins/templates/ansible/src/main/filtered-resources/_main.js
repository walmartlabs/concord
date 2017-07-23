({
    entryPoint: "main",
    dependencies: [
        "http://nexus.prod.walmart.com/nexus/content/repositories/devtools/com/walmartlabs/concord/plugins/basic/ansible-tasks/${project.version}/ansible-tasks-${project.version}.jar"
    ],
    arguments: {
        ansibleParams: _input
    }
});
