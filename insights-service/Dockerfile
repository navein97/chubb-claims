# Build context is the repo root (needs the parent pom + claims-events module)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY claims-events claims-events
COPY insights-service insights-service
COPY claims-service/pom.xml claims-service/pom.xml
RUN mvn -q -pl insights-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/insights-service/target/insights-service-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
