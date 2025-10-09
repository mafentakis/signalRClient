# syntax=docker/dockerfile:1

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY SignalRServer.java .
RUN javac SignalRServer.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/SignalRServer.class ./
CMD ["java", "SignalRServer"]
