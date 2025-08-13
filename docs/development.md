# Development Guide

This guide is for developers who wish to contribute to the project.

## Building the Project

To build the project without running tests:

```bash
./mvnw clean package -DskipTests
```

## Local Development Environment

To avoid repeatedly setting up the environment, you can first manually create a Kubernetes cluster and deploy the required services, and then run the tests against this setup.

1. Create a kind cluster:
```bash
kind create cluster --name secrets-provider --config testing/configs/kind-cluster-config.yaml
```

2. Deploy OpenBao and Keycloak:
```bash
kubectl apply -f testing/manifests
```

After this setup, you can run the tests against the manually created environment without needing to set up the cluster each time.

This setup will:
- Deploy OpenBao and Keycloak with two realms ("first" and "second").
- Create a user in the "second" realm for testing identity brokering with IdP secret using Vault SPI.
- Include the compiled JAR file in the Keycloak deployment (ensure you have built the project first).

Access Information:
- OpenBao: [http://127.0.0.127:8200](http://127.0.0.127:8200)
  Root token: `my-root-token`
- Keycloak: [http://127.0.0.127:8080](http://127.0.0.127:8080)
  Admin credentials: `admin` / `admin`
- Federated realm login: [http://127.0.0.127:8080/realms/first/account/](http://127.0.0.127:8080/realms/first/account/)
  User in "second" realm: `joe` / `joe`

To delete the cluster after testing, run:

```bash
kind delete cluster --name secrets-provider
```

## Running Integration Tests

> **Note:** Integration tests require [kind](https://kind.sigs.k8s.io/) and [kubectl](https://kubernetes.io/docs/tasks/tools/) to be installed.

To run integration tests against the manually setup environment (without `-DsetupEnv=true`):

```bash
./mvnw clean verify
```

## Running Checkstyle

To check code style compliance using Checkstyle, run:

```bash
./mvnw checkstyle:check
```

This will analyze the codebase and report any style violations.
