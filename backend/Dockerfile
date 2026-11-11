# syntax=docker/dockerfile:1
FROM maven:3.10.0-eclipse-temurin-17-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/backend-*.jar /app/app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
