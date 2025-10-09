package com.example.signalr;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Simple utility that connects to a SignalR hub and subscribes to events.
 */
public class SignalRSubscribe {
    public static void main(String[] args) throws InterruptedException {
        String defaultUrl = SignalRServer.BASE_HTTP_URL;
        String defaultEvents = "EventA,EventB";

        String url = args.length > 0 ? args[0] : defaultUrl;
        String eventsArg = args.length > 1 ? args[1] : "events=" + defaultEvents;

        if (!eventsArg.startsWith("events=") || eventsArg.substring("events=".length()).isEmpty()) {
            System.err.println("Event list missing. Provide events=EventA,EventB");
            System.exit(1);
        }

        validateUrl(url);

        String[] events = eventsArg.substring("events=".length()).split(",");
        String eventList = Arrays.stream(events).map(String::trim).filter(e -> !e.isEmpty())
                .collect(Collectors.joining(", "));
        if (eventList.isEmpty()) {
            System.err.println("No valid events supplied.");
            System.exit(1);
        }

        HubConnection hubConnection = HubConnectionBuilder.create(url).build();

        hubConnection.onClosed(error -> {
            if (error != null) {
                System.err.println("Connection closed with error: " + error.getMessage());
            } else {
                System.out.println("Connection closed.");
            }
        });

        Arrays.stream(events)
                .map(String::trim)
                .filter(event -> !event.isEmpty())
                .forEach(event ->
                        hubConnection.on(event, (Object message) -> System.out.println(event + ": " + message), Object.class)
                );

        System.out.println("Listening for events [" + eventList + "]");
        System.out.println("Connecting to " + url + "...");
        try {
            hubConnection.start().blockingAwait();
            System.out.println("Connected to " + url);
        } catch (Exception e) {
            System.err.println("Failed to connect: " + e.getMessage());
            System.exit(1);
        }

        new CountDownLatch(1).await();
    }

    private static void validateUrl(String url) {
        try {
            URI uri = new URI(url);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new URISyntaxException(url, "Missing scheme or host");
            }
        } catch (URISyntaxException e) {
            System.err.println("Invalid URL: " + url);
            System.exit(1);
        }
    }
}
