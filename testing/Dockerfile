FROM docker.io/eclipse-temurin:21 AS build

#  Copy directory
COPY . /keycloak-secrets-vault-provider
WORKDIR /keycloak-secrets-vault-provider

# Build the project and skip tests
RUN ./mvnw clean package -Dmaven.test.skip

# Keycloak image version to use as base
FROM quay.io/keycloak/keycloak:26.5.1

# Copy the  built .jar file to /opt/keycloak/providers path
COPY --from=build /keycloak-secrets-vault-provider/target/secrets-provider-*-SNAPSHOT. /opt/keycloak/providers/
