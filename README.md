# SignalRSubscribe

Utility that connects to a SignalR hub and prints events.

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
java -jar build/libs/signalRSubscribe.jar http://localhost:8080/hub events=EventA,EventB
```
- `http://localhost:8080/hub`: URL of the test hub exposed by the dev container
- `events=...`: comma-separated list of event names to subscribe to

## Development container

This project includes a VS Code [dev container](https://containers.dev/) setup
that runs a lightweight Java-based SignalR-compatible server for testing. The
sidecar is built with Gradle and uses the `com.microsoft.signalr:signalr:8.0.0`
library to encode hub messages. The server exposes a hub at `/hub` on port
`8080` and broadcasts random `EventA` and `EventB` messages every 2–5 seconds.

The container environment is based on Ubuntu and installs Java 17 and Gradle
for building the client.

The development environment is defined in `.devcontainer/` and can be used with
the VS Code Dev Containers extension or GitHub Codespaces.
