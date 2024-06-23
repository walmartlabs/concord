# Kubernetes Operator for Concord Agents

Takes care of deploying and scaling [Concord](https://concord.walmartlabs.com) 
Agents based on the current Process Queue usage. 

## Prerequisites

Build the parent Concord repo to install the latest artifacts and Docker
images:
```
$ cd concord/
$ ./mvnw clean install -DskipTests
```

## Running in Minikube

Below are the steps to deploy the concord agent operator to the `default`
namespace in any local/dev k8s cluster (in this case minikube).

Before deploying the operator's resources, please ensure the 
Concord Server URL and API token fields in the specs files are correctly
pointing  to a running instance on your dev or local machine. 
Make sure the API token used in the `operator.yml` is valid and working.

1. Start the cluster:
  ```
  $ minikube start
  $ minikube 
  ```
2. Build the operator's image:
  ```
  $ eval $(minikube docker-env)
  $ cd concord/agent-operator
  $ docker build . -t walmartlabs/concord-agent-operator:latest
  ```
3. Build the app's images (might take a while, depending on cached layers
present in your minikube instance):
  ```
  $ eval $(minikube docker-env)
  $ cd concord
  $ ./mvnw -f docker-images clean install -Pdocker
  ```
4. Deploy the necessary resources:
  ```
  $ minikube kubectl -- create -f deploy/cluster_role.yml -n default
  $ minikube kubectl -- create -f deploy/service_account.yml -n default
  $ minikube kubectl -- create -f deploy/cluster_role_binding.yml -n default
  ```
5. Create the custom resource:
  ```
  $ minikube kubectl -- create -f deploy/crds/crd.yml -n default
  $ minikube kubectl -- create -f deploy/crds/cr.yml -n default
  ```
6. Start the operator:
  ```
  $ minikube kubectl -- create -f deploy/operator.yml -n default
  ```

If everything is correct you should see this line in the operator's pod log:
```
[INFO ] c.w.concord.agentoperator.Operator - main -> my watch begins... (namespace=default)
[INFO ] c.w.c.a.p.CreateConfigMapChange - apply -> created a configmap example-agentpool-cfg
[INFO ] c.w.c.a.planner.CreatePodChange - apply -> created a pod example-agentpool/example-agentpool-00000
```
There should be no other errors or warnings.

## Running in an IDE

Repeat all steps from the [Running in Minikube](#running-in-minikube) section
except for "Start the operator".

Start the operator directly in your IDE by using `com.walmartlabs.concord.agentoperator.Operator`
as the main class. Specify `CONCORD_BASE_URL` and `CONCORD_API_TOKEN` if you
wish to test the autoscaling feature.

#### How to Verify

1. Check the pods running in the `default` namespace:
```
minikube kubectl -- get po -n default
```
The output should show two pods - 
the concord agent operator pod (with 1 container) and the agentpool pod (with 2 containers).

2. Verify the logs of both these pods using:
```
minikube kubectl -- logs -f <pod_name> -c <container_name> -n default
```

## Running in Production

1. Deploy the service account, the cluster role and the cluster role binding
(modify the commands according to your namespace):
  ```
  $ minikube kubectl -- create -f deploy/cluster_role.yml
  $ minikube kubectl -- create -f deploy/service_account.yml
  $ minikube kubectl -- create -f deploy/cluster_role_binding.yml
  ```
2. Update the `CONCORD_BASE_URL` and `CONCORD_API_TOKEN` in `deploy/operator.yml`.
   Verify the image name and tag (version);
3. Deploy the operator:
  ```
  $ minikube kubectl -- create -f deploy/operator.yml
  ```
4. Check the operator's pod logs;
5. Deploy one or more CRs using `deploy/crds/cr.yml` as a template.

## How To Release New Versions

- build the image;
- push the image to Docker Hub:
  ```
  $ docker push walmartlabs/concord-agent-operator:latest
  ```

## TODO

- automatically add the necessary labels to pods;
- add validation rules for CRDs and CRs;
- use secrets to store Concord API keys;
- make the queue connection optional if the auto scaling is disabled.
