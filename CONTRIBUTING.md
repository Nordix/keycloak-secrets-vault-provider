# Contributing

This guide is for those who wish to contribute to the project.

## Building the Project

To build the project without running tests:

```bash
./mvnw clean package -DskipTests
```

This compiles the source code and creates a JAR file in the `target` directory for use in subsequent steps.

## Local Development Environment

> **Note:** Integration tests require [kind](https://kind.sigs.k8s.io/) and [kubectl](https://kubernetes.io/docs/tasks/tools/) to be installed.

To avoid repeatedly setting up the environment during development, you can manually create a Kubernetes cluster and deploy the required services once, then run the tests against this setup.

### 1. Create a Kind cluster

```bash
./mvnw pre-integration-test -DmanageTestEnv=true -Dmaven.main.skip -Dmaven.test.skip
```

This command creates the test environment with the following components:

- Kubernetes cluster with kind
- Certificates for internal communication between Keycloak and OpenBao
- Keycloak with the compiled JAR, configured with two realms ("first" and "second") and a test user in the "second" realm for identity brokering tests
- OpenBao, initialized and unsealed, with KV secrets engine, roles and policies configured for Keycloak

**Note:** The `-Dmaven.main.skip` and `-Dmaven.test.skip` flags skip compilation during environment setup. To build the code during setup, add the Keycloak version profile, e.g., `./mvnw pre-integration-test -Pkeycloak-current -DmanageTestEnv=true`.

**Note:** After making code changes, rebuild with `./mvnw clean package -DskipTests` and restart Keycloak by running `kubectl delete pods -l app=keycloak`.

### 2. Run tests

Run the integration tests from your IDE or from the command line:

```bash
./mvnw clean verify
```

### 3. Delete the Kind cluster

```bash
./mvnw exec:exec@delete-kind-cluster -DmanageTestEnv=true
```

## Test Environment Access

The access information for test environments is as follows:

**OpenBao:** http://127.0.0.127:8200

To get the root token and unseal key:
```bash
kubectl exec $(kubectl get pod -l app=openbao -o jsonpath='{.items[0].metadata.name}') -c openbao-configurator -- cat /unseal/init.json
```

**Keycloak:**
- Instance 1 (keycloak-0): http://127.0.0.127:8080
- Instance 2 (keycloak-1): http://127.0.0.127:8081
- Admin credentials: `admin` / `admin`

**Identity brokering:** Pre-configured for interactive testing. Access the federated login at realm `first`: http://127.0.0.127:8080/realms/first/account/ and log in as user `joe` / `joe` from the `second` realm.



## Running Checkstyle

To check code style compliance using Checkstyle, run:

```bash
./mvnw checkstyle:check
```

This will analyze the codebase and report any style violations.
