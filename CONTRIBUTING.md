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

To avoid repeatedly setting up the environment during development, you can first manually create a Kubernetes cluster and deploy the required services, and then run the tests against this setup.

1. Create a Kind cluster

    ```bash
    ./mvnw pre-integration-test -DmanageTestEnv=true -Dmaven.main.skip -Dmaven.test.skip
    ```

    You can then run the tests against the manually created environment without needing to set up the cluster each time.

    The test setup will include:

    - Create Kubernetes cluster with kind.
    - Generate certificates for internal communication between Keycloak and OpenBao.
    - Deploy Keycloak with the compiled JAR, configure two realms ("first" and "second") and create a user in the "second" realm for testing identity brokering with IdP secret using Vault SPI.
    - Deploy OpenBao, initialize and unseal, configure KV secrets engine, and create a roles and policies for Keycloak.

    Note: If you make changes to the code, remember to rebuild the project using `./mvnw clean package -DskipTests` and restart Keycloak to pick up the changes by deleting the Keycloak pods `kubectl delete pods -l app=keycloak`.

    Note: `-Dmaven.main.skip` and `-Dmaven.test.skip` are used to skip building the main and test code during this setup phase.
    If you want to also build the code then add the Keycloak version profile you are working on, e.g. `./mvnw pre-integration-test -Pkeycloak-current -DmanageTestEnv=true`.

2. Run tests

    You can either run the integration tests from your IDE or from the command line.

    ```bash
    ./mvnw clean verify
    ```

3. Delete the Kind cluster

    ```bash
    ./mvnw exec:exec@delete-kind-cluster -DmanageTestEnv=true
    ```

The access information for test environments is as follows:

- OpenBao: http://127.0.0.127:8200

  To get the root token and unseal key run
  ```
  kubectl exec $(kubectl get pod -l app=openbao -o jsonpath='{.items[0].metadata.name}') -c openbao-configurator -- cat /unseal/init.json
  ```
- Keycloak: http://127.0.0.127:8080 (pod keycloak-0) and http://127.0.0.127:8081 (pod keycloak-1)

  Admin credentials: `admin` / `admin`

- Identity brokering has been pre-configured to Keycloak deployment for interactive testing. You can access the federated login at realm `first`: http://127.0.0.127:8080/realms/first/account/ and log in as user in `second` realm with credentials `joe` / `joe`



## Running Checkstyle

To check code style compliance using Checkstyle, run:

```bash
./mvnw checkstyle:check
```

This will analyze the codebase and report any style violations.
