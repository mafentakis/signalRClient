# syntax=docker/dockerfile:1

FROM gradle:8.7.0-jdk17-alpine AS build
WORKDIR /workspace
COPY build.gradle settings.gradle ./
COPY src ./src
RUN gradle --no-daemon fatJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/signalr-server-all.jar ./signalr-server.jar
EXPOSE 8080
CMD ["java", "-jar", "signalr-server.jar"]
