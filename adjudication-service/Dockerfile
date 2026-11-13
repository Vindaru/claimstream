# Multi-stage build: a big JDK image compiles the jar, a small JRE image runs it.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
# Every module POM must be present: Maven reads the whole reactor even when building one module.
COPY claims-domain/pom.xml claims-domain/pom.xml
COPY adjudication-engine/pom.xml adjudication-engine/pom.xml
COPY batch-intake/pom.xml batch-intake/pom.xml
COPY event-outbox/pom.xml event-outbox/pom.xml
COPY intake-service/pom.xml intake-service/pom.xml
COPY adjudication-service/pom.xml adjudication-service/pom.xml
# Download dependencies in their own layer so code-only changes rebuild fast.
RUN ./mvnw -q -B -pl adjudication-service -am dependency:go-offline
COPY claims-domain/src claims-domain/src
COPY adjudication-engine/src adjudication-engine/src
COPY event-outbox/src event-outbox/src
COPY adjudication-service/src adjudication-service/src
RUN ./mvnw -q -B -pl adjudication-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/adjudication-service/target/adjudication-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
