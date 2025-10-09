# SignalRSubscribe

Utility that contains a minimal SignalR-compatible server and a client that
subscribes to hub events.

## Build
```
gradle build --console=plain
```
The build compiles both the server (`SignalRServer`) and the client
(`SignalRSubscribe`). A fat jar containing all dependencies can be produced
with:

```
gradle fatJar --console=plain
```

The project targets Java 21. Use the included dev container (Java 21 base
image) or ensure that Java 21 and Gradle are available on the `PATH`.

## Run
Start the in-process test server (listening on port 8080):

```
gradle runServer --console=plain
```

Run the client against the local server (defaults to `http://localhost:8080` and
`EventA,EventB` when no arguments are supplied):

```
gradle runClient --console=plain
```

Pass custom arguments if needed:

```
gradle runClient --args='http://localhost:8080 events=EventA,EventB' --console=plain
```

Alternatively, execute the fat jar directly once built:
```
java -jar build/libs/signalRSubscribe.jar http://localhost:8080 events=EventA,EventB
```
- `http://localhost:8080`: URL of the test hub exposed by the bundled
  server
- `events=...`: comma-separated list of event names to subscribe to

The server logs the negotiation endpoint and WebSocket URL at startup and
broadcasts a random payload on either `EventA` or `EventB` every 2–5 seconds.
