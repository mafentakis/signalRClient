package com.example.signalr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple utility that connects to a SignalR hub and subscribes to events.
 */
public class SignalRSubscribe {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar signalRSubscribe.jar <url> [events=EventA,EventB]");
            System.exit(1);
        }

        String url = args[0];
        String eventsArg = args.length > 1 ? args[1] : "events=EventA,EventB";

        if (!eventsArg.startsWith("events=")) {
            System.err.println("Event list missing. Provide events=EventA,EventB");
            System.exit(1);
        }

        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new URISyntaxException(url, "Missing scheme or host");
            }
        } catch (URISyntaxException e) {
            System.err.println("Invalid URL: " + url);
            System.exit(1);
        }

        String[] events = Arrays.stream(eventsArg.substring("events=".length()).split(","))
                .map(String::trim)
                .filter(event -> !event.isEmpty())
                .toArray(String[]::new);

        if (events.length == 0) {
            System.err.println("No events provided.");
            System.exit(1);
        }

        HubConnection hubConnection = HubConnectionBuilder.create(url).build();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicBoolean shutdownRequested = new AtomicBoolean(false);

        hubConnection.onClosed(error -> {
            if (error != null) {
                System.err.println("Connection closed with error: " + error.getMessage());
            } else {
                System.out.println("Connection closed.");
            }
        });

        Arrays.stream(events).forEach(event ->
                hubConnection.on(event, (Object message) -> System.out.println(event + ": " + stringifyMessage(objectMapper, message)), Object.class)
        );

        System.out.println("Connecting to " + url + "...");
        try {
            hubConnection.start().blockingAwait();
            System.out.println("Connected to " + url);
        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (shutdownRequested.compareAndSet(false, true)) {
                System.out.println("Shutting down...");
                hubConnection.stop().blockingAwait();
            }
        }));

        new CountDownLatch(1).await();
    }

    private static String stringifyMessage(ObjectMapper objectMapper, Object message) {
        if (message == null) {
            return "null";
        }

        if (message instanceof String) {
            return (String) message;
        }

        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            return message.toString();
        }
    }
}
