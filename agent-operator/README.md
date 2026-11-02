# Kubernetes Operator for Concord Agents

Deploy and scale [Concord](https://concord.walmartlabs.com) Agents based on
current Process Queue usage.

## Prerequisites

Build the parent Concord repository. This installs the latest artifacts and
Docker images:
```
$ cd concord/
$ ./mvnw clean install -DskipTests
```

## Running in Minikube

Do these steps to deploy the Concord Agent Operator to the `default` namespace.
Use a local or development Kubernetes cluster. This example uses Minikube.

Before you deploy the operator resources, check the Concord Server URL and API token in the specification files.
Make sure that they refer to a server that is in operation.
Check that the API token in `operator.yml` is valid.

1. Start the cluster:
  ```
  $ minikube start
  $ minikube 
  ```
2. Build the operator image:
  ```
  $ eval $(minikube docker-env)
  $ cd docker-images/agent-operator
  $ docker build . -t walmartlabs/concord-agent-operator:latest
  ```
3. Build the application images. This can take time if Minikube does not have
cached image layers.
  ```
  $ eval $(minikube docker-env)
  $ cd concord
  $ ./mvnw -f docker-images clean install -Pdocker
  ```
4. Deploy the required resources:
  ```
  $ minikube kubectl -- create -f deploy/cluster_role.yml -n default
  $ minikube kubectl -- create -f deploy/service_account.yml -n default
  $ minikube kubectl -- create -f deploy/cluster_role_binding.yml -n default
  ```
5. Create the custom resource:
  ```
  $ minikube kubectl -- create -f deploy/crds/crd.yml -n default
  $ minikube kubectl -- create -f deploy/crds/example.agentpool.yml -n default
  ```
6. Start the operator:
  ```
  $ minikube kubectl -- create -f deploy/operator.yml -n default
  ```

Check for these lines in the operator pod log:
```
[INFO ] c.w.concord.agentoperator.Operator - main -> my watch begins... (namespace=default)
[INFO ] c.w.c.a.p.CreateConfigMapChange - apply -> created a configmap example-agentpool-cfg
[INFO ] c.w.c.a.planner.CreatePodChange - apply -> created a pod example-agentpool/example-agentpool-00000
```
The expected log contains no other errors or warnings.

## Running in an IDE

Repeat the steps in [Running in Minikube](#running-in-minikube), but do not
start the operator with the deployment command.

Run `com.walmartlabs.concord.agentoperator.Operator` as the main class in your
IDE. To test autoscaling, set `CONCORD_BASE_URL` and `CONCORD_API_TOKEN`.

#### How to Verify

1. Check the pods in the `default` namespace:
```
minikube kubectl -- get po -n default
```
The expected output shows two pods: the Concord Agent Operator pod with one
container, and the agentpool pod with two containers.

2. Check the logs for both pods:
```
minikube kubectl -- logs -f <pod_name> -c <container_name> -n default
```
## Running in Production

1. Deploy the service account, cluster role, and cluster role binding. Change
the commands to use your namespace:
  ```
  $ minikube kubectl -- create -f deploy/cluster_role.yml
  $ minikube kubectl -- create -f deploy/service_account.yml
  $ minikube kubectl -- create -f deploy/cluster_role_binding.yml
  ```
2. Set `CONCORD_BASE_URL` and `CONCORD_API_TOKEN` in `deploy/operator.yml`.
   Check the image name and tag (version).
3. Deploy the operator:
  ```
  $ minikube kubectl -- create -f deploy/operator.yml
  ```
4. Check the operator pod logs.
5. Deploy one or more custom resources (CRs). Use
   `deploy/crds/example.agentpool.yml` as a template.

## Debug deployed agent-operator pod

Edit the operator deployment. Add the `JAVA_TOOL_OPTIONS` environment variable:

```
# ...
      containers:
      # ...
      - name: concord-agent-operator
        env:
          - name: JAVA_TOOL_OPTIONS
            value: "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
      # ...
```

Set `suspend=y` to make the JVM wait for a debugger before it executes
`Operator.main(String args[])`.

Then, forward the pod debugger port to a local port:

```
# forward local port 5006 to pod port 5005
$ kubectl port-forward -n default pod/<agent-operator-pod-name> 5006:5005
```

## How To Release New Versions

- Build the image.
- Push the image to Docker Hub:
  ```
  $ docker push walmartlabs/concord-agent-operator:latest
  ```

## TODO

- Automatically add the necessary labels to pods.
- Add validation rules for CRDs and CRs.
- Use secrets to store Concord API keys.
- Make the queue connection optional if auto scaling is disabled.
