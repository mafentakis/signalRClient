# SignalRSubscribe

Utility that connects to a SignalR hub and prints events.

## Build
```
gradle fatJar --console=plain
```
The jar is created at `build/libs/signarRSubscribe.jar`.

## Run
```
java -jar build/libs/signarRSubscribe.jar http://localhost:8080/hub events=EventA,EventB
```
- `http://localhost:8080/hub`: URL of the test hub exposed by the dev container
- `events=...`: comma-separated list of event names to subscribe to

## Development container

This project includes a VS Code [dev container](https://containers.dev/) setup
that runs a lightweight .NET SignalR server for testing. The server exposes a
hub at `/hub` on port `8080` and broadcasts `EventA` and `EventB` events with
random strings every 2–5 seconds.

The development environment is defined in `.devcontainer/` and can be used with
the VS Code Dev Containers extension or GitHub Codespaces.
