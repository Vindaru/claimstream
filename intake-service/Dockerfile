# Multi-stage build: a big JDK image compiles the jar, a small JRE image runs it.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY config config
COPY intake-service/pom.xml intake-service/pom.xml
# Download dependencies in their own layer so code-only changes rebuild fast.
RUN ./mvnw -q -B -pl intake-service -am dependency:go-offline
COPY intake-service/src intake-service/src
RUN ./mvnw -q -B -pl intake-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/intake-service/target/intake-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
