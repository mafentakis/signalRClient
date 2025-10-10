# SignalRSubscribe

Utility that connects to a SignalR hub and prints events.

The repository now also contains a minimal ASP.NET Core SignalR server that
emits `EventA` and `EventB` payloads every second. You can run it via Docker
and use this client to observe the traffic.

## Build
```
gradle fatJar --console=plain
```
The jar is created at `build/libs/signalRSubscribe.jar`.
The project expects Gradle and a Java 17 JDK to be available on the PATH.
The included development container installs both tools so a wrapper script
is not required.

## Run
```
java -jar build/libs/signalRSubscribe.jar http://localhost:8080/hub/events
java -jar build/libs/signalRSubscribe.jar http://localhost:8080/hub/events events=EventA,EventB
```
- `http://localhost:8080/hub/events`: URL of the SignalR hub endpoint
- `events=...`: optional comma-separated list of event names to subscribe to
  (defaults to `EventA,EventB` if omitted)

Use `Ctrl+C` to stop the client gracefully.

## Sample SignalR server (Docker)

The `server/` directory provides a small ASP.NET Core server that continuously
emits `EventA` and `EventB` payloads. You can run it locally with Docker:

```
cd server
docker compose up --build
```

The server listens on `http://localhost:8080/hub/events`. With the container
running you can connect the Java client from another terminal:

```
./gradlew fatJar
java -jar build/libs/signalRSubscribe.jar http://localhost:8080/hub/events
```

Press `Ctrl+C` in both terminals to stop the client and server.
