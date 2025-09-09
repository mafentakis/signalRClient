# SignalRSubscribe

Utility that connects to a SignalR hub and prints events.

## Build
```
gradle fatJar --console=plain
```
The jar is created at `build/libs/signarRSubscribe.jar`.

## Run
```
java -jar build/libs/signarRSubscribe.jar <url> events=EventA,EventB
```
- `<url>`: SignalR hub URL
- `events=...`: comma-separated list of event names
