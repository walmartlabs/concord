# Kubernetes Operator for Concord Agents

Takes care of deploying and scaling [Concord](https://concord.walmartlabs.com) 
Agents based on the current Process Queue usage. 

## Building the Image

First, build the parent concord repo to install the latest artifacts:
```
$ cd concord/
$ mvn clean install
```
Build the agent operator image:
```
$ cd concord/k8s/agent-operator
$ ../../mvnw clean compile jib:dockerBuild
```

## Running in Dev Mode

Below are the steps to deploy the concord agent operator to the `default` namespace
in any local/dev k8s cluster (in this case minikube).

Before deploying the operator's resources, please ensure the 
concord server URL fields in the yaml specs are correctly pointing 
to a running instance on your dev or local machine. 
Also make sure that the API token used in the `operator.yml` is valid and working.

1. Start the cluster and create the necessary resources:
  ```
  $ minikube start
  $ kubectl create -f deploy/cluster_role.yml -n default
  $ kubectl create -f deploy/service_account.yml -n default
  $ kubectl create -f deploy/cluster_role_binding.yml -n default
  ```
2. Start the operator:
  ```
  $ kubectl create -f deploy/operator.yml -n default
  ```
3. Create the custom resource:
  ```
  $ kubectl create -f deploy/crds/crd.yml -n default
  $ kubectl create -f deploy/crds/cr.yml -n default
  ```

#### How to Verify

1. Check the pods running in the `default` namespace:
```
kubectl get po -n default
```
The output should show two pods - 
the concord agent operator pod (with 1 container) and the agentpool pod (with 2 containers).

2. Verify the logs of both these pods using:
```
kubectl logs -f <pod_name> -c <container_name> -n default
```

## Running in Production

1. Deploy the service account, the cluster role and the cluster role binding
(modify the commands according to your namespace):
  ```
  $ kubectl create -f deploy/cluster_role.yml
  $ kubectl create -f deploy/service_account.yml
  $ kubectl create -f deploy/cluster_role_binding.yml
  ```
2. Update the `CONCORD_BASE_URL` and `CONCORD_API_TOKEN` in `deploy/operator.yml`.
   Verify the image name and tag (version);
3. Deploy the operator:
  ```
  $ kubectl create -f deploy/operator.yml
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
