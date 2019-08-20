# Kubernetes Operator for Concord Agents

Takes care of deploying and scaling [Concord](https://concord.walmartlabs.com)
Agents based on the current Process Queue usage. 

## Building the Image

```
$ ../../mvnw clean compile jib:dockerBuild
```

## Running in Dev Mode

1. Start the cluster and create the necessary resources:
  ```
  $ minikube start
  $ kubectl create -f deploy/cluster_role.yml
  $ kubectl create -f deploy/service_account.yml
  $ kubectl create -f deploy/cluster_role_binding.yml
  ```
2. Start `com.walmartlabs.concord.agentoperator.Operator` in your IDE;
3. Create the service:
  ```
  $ kubectl create -f deploy/crds/crd.yml
  $ kubectl create -f deploy/crds/cr.yml
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
