variable "DOCKER_NAMESPACE" {
  default = "walmartlabs"
}

variable "DOCKER_TAG" {
  default = "latest"
}

variable "JDK_VERSION" {
  default = "25"
}

group "default" {
  targets = [
    "concord-base",
    "concord-ansible",
    "concord-agent",
    "concord-server",
    "concord-agent-operator",
  ]
}

target "_common" {
  platforms = ["linux/amd64"]
}

target "concord-base" {
  inherits = ["_common"]
  context = "./base"
  dockerfile = "oss/debian/Dockerfile"
  tags = ["${DOCKER_NAMESPACE}/concord-base:${DOCKER_TAG}"]
  args = {
    jdk_version = JDK_VERSION
  }
}

target "concord-ansible" {
  inherits = ["_common"]
  context = "./ansible"
  dockerfile = "oss/debian/Dockerfile"
  tags = ["${DOCKER_NAMESPACE}/concord-ansible:${DOCKER_TAG}"]
  args = {
    concord_base_image = "concord-base"
    concord_version = DOCKER_TAG
    docker_namespace = DOCKER_NAMESPACE
  }
  contexts = {
    "concord-base" = "target:concord-base"
  }
}

target "concord-agent" {
  inherits = ["_common"]
  context = "./agent"
  dockerfile = "oss/debian/Dockerfile"
  tags = ["${DOCKER_NAMESPACE}/concord-agent:${DOCKER_TAG}"]
  args = {
    concord_ansible_image = "concord-ansible"
    concord_version = DOCKER_TAG
    docker_namespace = DOCKER_NAMESPACE
  }
  contexts = {
    "concord-ansible" = "target:concord-ansible"
  }
}

target "concord-server" {
  inherits = ["_common"]
  context = "./server"
  dockerfile = "oss/Dockerfile"
  tags = ["${DOCKER_NAMESPACE}/concord-server:${DOCKER_TAG}"]
  args = {
    concord_base_image = "concord-base"
    concord_version = DOCKER_TAG
    docker_namespace = DOCKER_NAMESPACE
  }
  contexts = {
    "concord-base" = "target:concord-base"
  }
}

target "concord-agent-operator" {
  inherits = ["_common"]
  context = "./agent-operator"
  dockerfile = "oss/Dockerfile"
  tags = ["${DOCKER_NAMESPACE}/concord-agent-operator:${DOCKER_TAG}"]
}
