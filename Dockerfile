FROM registry.access.redhat.com/ubi9 AS ubi-micro-build
# Install Java 21 JDK and other required packages for the testing
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs vi curl java-21-openjdk-devel \
    --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

# Install maven
RUN dnf install -y maven 

#  Copy directory
COPY . /keycloak-secrets-vault-provider
WORKDIR /keycloak-secrets-vault-provider

# Build with maven
RUN mvn clean package

FROM quay.io/keycloak/keycloak:26.4.0

COPY --from=ubi-micro-build /mnt/rootfs /

# Copy the  built .jar file to /opt/keycloak/providers path
COPY --from=ubi-micro-build /keycloak-secrets-vault-provider/target/secrets-provider-1.1.0-SNAPSHOT.jar /opt/keycloak/providers/