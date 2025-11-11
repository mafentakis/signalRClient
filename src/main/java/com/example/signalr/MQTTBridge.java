package com.example.signalr;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT Bridge that subscribes to SignalR events and publishes them to an MQTT broker.
 * Implements automatic reconnection with a 2-second pause on SignalR connection failures.
 */
public class MQTTBridge {
    private static final int RECONNECT_DELAY_SECONDS = 2;
    private static final int MQTT_QOS = 1;
    private static final String MQTT_TOPIC_PREFIX = "signalr/";
    
    private final String signalRUrl;
    private final String[] events;
    private final String mqttBrokerUrl;
    private final String mqttClientId;
    
    private HubConnection hubConnection;
    private MqttClient mqttClient;
    private volatile boolean running = true;

    public MQTTBridge(String signalRUrl, String[] events, String mqttBrokerUrl, String mqttClientId) {
        this.signalRUrl = signalRUrl;
        this.events = events;
        this.mqttBrokerUrl = mqttBrokerUrl;
        this.mqttClientId = mqttClientId;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java -jar MQTTBridge.jar <signalr-url> events=EventA,EventB <mqtt-broker-url> [mqtt-client-id]");
            System.err.println("Example: java -jar MQTTBridge.jar http://localhost:5000/eventhub events=EventA,EventB tcp://localhost:1883 mqtt-bridge-client");
            System.exit(1);
        }

        String signalRUrl = args[0];
        String eventsArg = args[1];
        String mqttBrokerUrl = args[2];
        String mqttClientId = args.length > 3 ? args[3] : "mqtt-bridge-" + System.currentTimeMillis();

        // Validate SignalR URL
        if (!eventsArg.startsWith("events=") || eventsArg.substring("events=".length()).isEmpty()) {
            System.err.println("Event list missing. Provide events=EventA,EventB");
            System.exit(1);
        }

        try {
            URI uri = new URI(signalRUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new URISyntaxException(signalRUrl, "Missing scheme or host");
            }
        } catch (URISyntaxException e) {
            System.err.println("Invalid SignalR URL: " + signalRUrl);
            System.exit(1);
        }

        String[] events = eventsArg.substring("events=".length()).split(",");
        
        MQTTBridge bridge = new MQTTBridge(signalRUrl, events, mqttBrokerUrl, mqttClientId);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down MQTT Bridge...");
            bridge.shutdown();
        }));
        
        bridge.start();
    }

    public void start() throws Exception {
        // Initialize MQTT client
        initializeMqttClient();

        // Start SignalR connection with retry logic
        connectToSignalR();

        // Keep the application running
        new CountDownLatch(1).await();
    }

    private void initializeMqttClient() throws MqttException {
        System.out.println("Connecting to MQTT broker: " + mqttBrokerUrl);

        mqttClient = new MqttClient(mqttBrokerUrl, mqttClientId, new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("MQTT connection lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                // Not used - we only publish
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // Message delivery confirmed
            }
        });

        mqttClient.connect(options);
        System.out.println("Connected to MQTT broker: " + mqttBrokerUrl);
    }

    private void connectToSignalR() {
        System.out.println("Connecting to SignalR hub: " + signalRUrl);

        hubConnection = HubConnectionBuilder.create(signalRUrl).build();

        // Set up connection closed handler with reconnection logic
        setupRetry(hubConnection);

        // Start the connection
        try {
            hubConnection.start().blockingAwait();
            System.out.println("Connected to SignalR hub: " + signalRUrl);

            // Subscribe to events after connection is established
            Arrays.stream(events).forEach(event -> {
                hubConnection.on(event, (Object message) -> {
                    handleSignalREvent(event, message);
                }, Object.class);
            });

            System.out.println("Subscribed to events: " + String.join(", ", events));
            System.out.println("Publishing to MQTT topic prefix: " + MQTT_TOPIC_PREFIX);
        } catch (Exception e) {
            System.err.println("Failed to connect to SignalR: " + e.getMessage());
            // Trigger reconnection via onClosed handler
            if (running) {
                System.out.println("Retrying in " + RECONNECT_DELAY_SECONDS + " seconds...");
                try {
                    TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
                    connectToSignalR();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Reconnection interrupted: " + ie.getMessage());
                }
            }
        }
    }

    private void setupRetry(final HubConnection hubConnection) {
        hubConnection.onClosed(error -> {
            if (error != null) {
                System.err.println("SignalR connection closed with error: " + error.getMessage());
            } else {
                System.out.println("SignalR connection closed.");
            }

            // Reconnect if still running
            if (running) {
                System.out.println("Reconnecting in " + RECONNECT_DELAY_SECONDS + " seconds...");
                try {
                    TimeUnit.SECONDS.sleep(RECONNECT_DELAY_SECONDS);
                    connectToSignalR();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Reconnection interrupted: " + e.getMessage());
                }
            }
        });
    }

    private void handleSignalREvent(String eventName, Object message) {
        try {
            String topic = MQTT_TOPIC_PREFIX + eventName;
            String payload = message != null ? message.toString() : "";

            System.out.println("Received SignalR event [" + eventName + "]: " + payload);

            // Publish to MQTT
            MqttMessage mqttMessage = new MqttMessage(payload.getBytes());
            mqttMessage.setQos(MQTT_QOS);
            mqttMessage.setRetained(false);

            mqttClient.publish(topic, mqttMessage);
            System.out.println("Published to MQTT topic [" + topic + "]: " + payload);

        } catch (MqttException e) {
            System.err.println("Failed to publish to MQTT: " + e.getMessage());
        }
    }

    public void shutdown() {
        running = false;
        
        // Close SignalR connection
        if (hubConnection != null) {
            try {
                hubConnection.stop().blockingAwait();
                System.out.println("SignalR connection closed.");
            } catch (Exception e) {
                System.err.println("Error closing SignalR connection: " + e.getMessage());
            }
        }
        
        // Close MQTT connection
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                System.out.println("MQTT connection closed.");
            } catch (MqttException e) {
                System.err.println("Error closing MQTT connection: " + e.getMessage());
            }
        }
    }
}

