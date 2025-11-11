# SignalRSubscribe

Java SignalR client that connects to a SignalR hub and subscribes to events.

Includes an **MQTT Bridge** that forwards SignalR events to an MQTT broker.

## Quick Start

### 1. Start the .NET SignalR Server (Docker)

```bash
docker-compose -f server/docker-compose.yml up --build -d
```

Server will be available at `http://localhost:5000/eventhub`

### 2. Build and Run the Java Client

```bash
gradle fatJar && java -jar build/libs/signalRSubscribe.jar http://localhost:5000/eventhub events=EventA,EventB
```

### 3. Stop the Server

```bash
docker-compose -f server/docker-compose.yml down
```

## Expected Output

**Client:**
```
Connecting to http://localhost:5000/eventhub...
Connected to http://localhost:5000/eventhub
EventA: EventA message #1 at 14:23:45
EventB: EventB message #1 at 14:23:46
EventA: EventA message #2 at 14:23:47
EventB: EventB message #2 at 14:23:48
...
```

## Usage

### SignalR Subscribe Client

```bash
java -jar build/libs/signalRSubscribe.jar <url> events=<event1>,<event2>,...
```

**Arguments:**
- `<url>` - SignalR hub URL (e.g., `http://localhost:5000/eventhub`)
- `events=...` - Comma-separated list of event names to subscribe to

**Examples:**
```bash
# Subscribe to multiple events
java -jar build/libs/signalRSubscribe.jar http://localhost:5000/eventhub events=EventA,EventB

# Subscribe to single event
java -jar build/libs/signalRSubscribe.jar http://localhost:5000/eventhub events=EventA
```

### MQTT Bridge

The MQTT Bridge subscribes to SignalR events and publishes them to an MQTT broker.

**Build:**
```bash
gradle mqttBridgeJar
```

**Run:**
```bash
java -jar build/libs/MQTTBridge.jar <signalr-url> events=<event1>,<event2> <mqtt-broker-url> [mqtt-client-id]
```

**Arguments:**
- `<signalr-url>` - SignalR hub URL (e.g., `http://localhost:5000/eventhub`)
- `events=...` - Comma-separated list of event names to subscribe to
- `<mqtt-broker-url>` - MQTT broker URL (e.g., `tcp://localhost:1883`)
- `[mqtt-client-id]` - Optional MQTT client ID (default: `mqtt-bridge-<timestamp>`)

**Example:**
```bash
# Start MQTT broker (using Mosquitto)
docker run -d -p 1883:1883 -p 9001:9001 --name mosquitto eclipse-mosquitto:latest

# Run the MQTT Bridge
java -jar build/libs/MQTTBridge.jar http://localhost:5000/eventhub events=EventA,EventB tcp://localhost:1883

# Subscribe to MQTT topics to see forwarded events
mosquitto_sub -h localhost -t "signalr/#" -v
```

**Features:**
- ✅ Subscribes to SignalR events
- ✅ Publishes events to MQTT topics with prefix `signalr/<event-name>`
- ✅ Automatic reconnection with 2-second pause on SignalR connection failure
- ✅ Automatic MQTT reconnection
- ✅ QoS 1 for reliable message delivery
- ✅ Graceful shutdown handling

## Project Structure

```
├── src/main/java/com/example/signalr/
│   ├── SignalRSubscribe.java          # Java SignalR client
│   └── MQTTBridge.java                # MQTT Bridge (SignalR → MQTT)
├── server/
│   ├── SignalRServer/
│   │   ├── Program.cs                 # Server entry point
│   │   ├── Hubs/EventHub.cs           # SignalR hub
│   │   ├── EventEmitterService.cs     # Emits EventA/EventB every second
│   │   └── SignalRServer.csproj       # .NET 8 project
│   ├── Dockerfile                     # Docker image
│   └── docker-compose.yml             # Docker Compose config
└── build.gradle                       # Gradle build config
```

## Server Details

- **Port:** 5000
- **Hub Endpoint:** `/eventhub`
- **Events:** Emits `EventA` and `EventB` alternately every second
- **Technology:** .NET 8 SignalR with Docker

## Requirements

- **Docker & Docker Compose** (for server)
- **Java 17+** (for client)
- **Gradle** (included wrapper)

## Development Container

This project includes a VS Code [dev container](https://containers.dev/) with:
- ✅ Java 21
- ✅ Gradle
- ✅ Docker-in-Docker (for running the SignalR server)
- ✅ Java extensions for VS Code

**To use:**
1. Install [VS Code](https://code.visualstudio.com/) and the [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
2. Open this project in VS Code
3. Click "Reopen in Container" when prompted (or use Command Palette: `Dev Containers: Reopen in Container`)
4. All tools will be pre-installed and ready to use

