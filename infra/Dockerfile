# Download dependencies stage
FROM maven:3-eclipse-temurin-22-jammy AS dependencies

WORKDIR /opt/maven

COPY --link pom.xml .

RUN mvn dependency:go-offline


# Build stage
FROM maven:3-eclipse-temurin-22-jammy

WORKDIR /opt/maven

COPY --from=dependencies /root/.m2 /root/.m2

COPY --link . .
