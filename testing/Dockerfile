FROM docker.io/eclipse-temurin:21 AS build

#  Copy directory
COPY . /keycloak-secrets-vault-provider
WORKDIR /keycloak-secrets-vault-provider

# Build the project and skip tests
RUN ./mvnw clean package -Dmaven.test.skip

# Keycloak image version 26.4.4
FROM quay.io/keycloak/keycloak:26.4.4

# Copy the  built .jar file to /opt/keycloak/providers path
COPY --from=build /keycloak-secrets-vault-provider/target/secrets-provider-*-SNAPSHOT. /opt/keycloak/providers/