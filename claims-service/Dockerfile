# Build context is the repo root (needs the parent pom + claims-events module)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY claims-events claims-events
COPY claims-service claims-service
COPY insights-service/pom.xml insights-service/pom.xml
RUN mvn -q -pl claims-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/claims-service/target/claims-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
