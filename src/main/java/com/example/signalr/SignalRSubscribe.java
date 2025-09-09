package com.example.signalr;

import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Simple utility that connects to a SignalR hub and subscribes to events.
 */
public class SignalRSubscribe {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.err.println("Usage: java -jar signarRSubscribe.jar <url> events=EventA,EventB");
            System.exit(1);
        }

        String url = args[0];
        String eventsArg = args[1];

        if (!eventsArg.startsWith("events=") || eventsArg.substring("events=".length()).isEmpty()) {
            System.err.println("Event list missing. Provide events=EventA,EventB");
            System.exit(1);
        }

        try {
            new URL(url);
        } catch (MalformedURLException e) {
            System.err.println("Invalid URL: " + url);
            System.exit(1);
        }

        String[] events = eventsArg.substring("events=".length()).split(",");
        HubConnection hubConnection = HubConnectionBuilder.create(url).build();

        hubConnection.onClosed(error -> {
            if (error != null) {
                System.err.println("Connection closed with error: " + error.getMessage());
            } else {
                System.out.println("Connection closed.");
            }
        });

        Arrays.stream(events).forEach(event ->
                hubConnection.on(event, (Object message) -> System.out.println(event + ": " + message), Object.class)
        );

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
}
