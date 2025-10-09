package com.example.signalr;

import com.microsoft.signalr.GsonHubProtocol;
import com.microsoft.signalr.InvocationMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal SignalR-compatible server that negotiates WebSocket connections and
 * emits EventA/EventB messages every few seconds.
 */
public final class SignalRServer {
    public static final int PORT = 8080;
    private static final String HOST = "localhost";
    public static final String BASE_HTTP_URL = "http://" + HOST + ":" + PORT;
    public static final String WEBSOCKET_URL = "ws://" + HOST + ":" + PORT + "/";
    private static final GsonHubProtocol HUB_PROTOCOL = new GsonHubProtocol();
    private static final byte[] HANDSHAKE_ACK = "{}\u001e".getBytes(StandardCharsets.UTF_8);
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(4);
    private static final Random RANDOM = new Random();

    private SignalRServer() {
    }

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Java SignalR test server on port " + PORT);
        System.out.println("Negotiation endpoint: " + BASE_HTTP_URL + "/negotiate");
        System.out.println("WebSocket endpoint: " + WEBSOCKET_URL);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                EXECUTOR.submit(() -> handleConnection(socket));
            }
        }
    }

    private static void handleConnection(Socket socket) {
        try (socket) {
            socket.setSoTimeout(0);
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            HttpRequest request = HttpRequest.readFrom(input);
            if (request == null) {
                return;
            }

            if ("GET".equals(request.method) && request.path.startsWith("/negotiate")) {
                handleNegotiate(output);
                return;
            }

            if (!"websocket".equalsIgnoreCase(request.headers.get("upgrade")) ||
                    !request.headers.getOrDefault("connection", "").toLowerCase(Locale.ROOT).contains("upgrade")) {
                respondNotFound(output);
                return;
            }

            String secKey = request.headers.get("sec-websocket-key");
            if (secKey == null) {
                respondBadRequest(output);
                return;
            }

            performWebSocketHandshake(secKey, output);
            handleHubMessages(input, output);
        } catch (IOException e) {
            // Ignore broken connections.
        }
    }

    private static void handleNegotiate(OutputStream output) throws IOException {
        String body = "{" +
                "\"connectionId\":\"" + UUID.randomUUID() + "\"," +
                "\"availableTransports\":[{" +
                "\"transport\":\"WebSockets\"," +
                "\"transferFormats\":[\"Text\",\"Binary\"]" +
                "}]," +
                "\"negotiateVersion\":1" +
                "}";
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + payload.length + "\r\n" +
                "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(payload);
        output.flush();
    }

    private static void performWebSocketHandshake(String secKey, OutputStream output) throws IOException {
        try {
            String accept = createAcceptKey(secKey);
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (NoSuchAlgorithmException e) {
            respondServerError(output);
            throw new IOException("Unable to create WebSocket handshake", e);
        }
    }

    private static void handleHubMessages(InputStream input, OutputStream output) throws IOException {
        boolean handshakeCompleted = false;
        while (true) {
            WebSocketFrame frame = WebSocketFrame.read(input);
            if (frame == null) {
                break;
            }

            switch (frame.opcode) {
                case WebSocketFrame.TEXT:
                    if (!handshakeCompleted) {
                        sendHandshakeAck(output);
                        handshakeCompleted = true;
                        scheduleNextEvent(output);
                    }
                    break;
                case WebSocketFrame.CLOSE:
                    sendCloseFrame(output, frame.payloadData);
                    return;
                case WebSocketFrame.PING:
                    sendPongFrame(output, frame.payloadData);
                    break;
                default:
                    break;
            }
        }
    }

    private static void sendHandshakeAck(OutputStream output) throws IOException {
        sendTextFrame(output, HANDSHAKE_ACK);
    }

    private static void scheduleNextEvent(OutputStream output) {
        long delayMillis = 2000L + RANDOM.nextInt(3001);
        SCHEDULER.schedule(() -> {
            try {
                String eventName = RANDOM.nextBoolean() ? "EventA" : "EventB";
                InvocationMessage message = new InvocationMessage(Collections.emptyMap(), null, eventName,
                        new Object[]{randomPayload()}, Collections.emptyList());
                byte[] data = bytesFromBuffer(HUB_PROTOCOL.writeMessage(message));
                sendTextFrame(output, data);
                scheduleNextEvent(output);
            } catch (IOException e) {
                // Connection likely closed; stop scheduling further events.
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static String randomPayload() {
        return "Payload-" + UUID.randomUUID();
    }

    private static void sendCloseFrame(OutputStream output, byte[] payload) throws IOException {
        WebSocketFrame.closeFrame(payload).writeTo(output);
    }

    private static void sendPongFrame(OutputStream output, byte[] payload) throws IOException {
        WebSocketFrame.pongFrame(payload).writeTo(output);
    }

    private static void sendTextFrame(OutputStream output, byte[] payload) throws IOException {
        WebSocketFrame.textFrame(payload).writeTo(output);
    }

    private static byte[] bytesFromBuffer(java.nio.ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static void respondNotFound(OutputStream output) throws IOException {
        sendSimpleResponse(output, "404 Not Found", "Not Found");
    }

    private static void respondBadRequest(OutputStream output) throws IOException {
        sendSimpleResponse(output, "400 Bad Request", "Bad Request");
    }

    private static void respondServerError(OutputStream output) throws IOException {
        sendSimpleResponse(output, "500 Internal Server Error", "Internal Server Error");
    }

    private static void sendSimpleResponse(OutputStream output, String statusLine, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + statusLine + "\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + payload.length + "\r\n" +
                "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.UTF_8));
        output.write(payload);
        output.flush();
    }

    private static String createAcceptKey(String key) throws NoSuchAlgorithmException {
        String guid = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hashed = digest.digest((key + guid).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(hashed);
    }

    private static final class HttpRequest {
        private final String method;
        private final String path;
        private final java.util.Map<String, String> headers;

        private HttpRequest(String method, String path, java.util.Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.headers = headers;
        }

        private static HttpRequest readFrom(InputStream input) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }

            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) {
                return null;
            }

            java.util.Map<String, String> headers = new java.util.HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String name = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(idx + 1).trim();
                    headers.put(name, value);
                }
            }

            return new HttpRequest(parts[0], parts[1], headers);
        }
    }

    private static final class WebSocketFrame {
        private static final int TEXT = 0x1;
        private static final int CLOSE = 0x8;
        private static final int PING = 0x9;
        private static final int PONG = 0xA;

        private final int opcode;
        private final byte[] payloadData;

        private WebSocketFrame(int opcode, byte[] payloadData) {
            this.opcode = opcode;
            this.payloadData = payloadData != null ? payloadData : new byte[0];
        }

        private static WebSocketFrame textFrame(byte[] payload) {
            return new WebSocketFrame(TEXT, payload);
        }

        private static WebSocketFrame closeFrame(byte[] payload) {
            return new WebSocketFrame(CLOSE, payload);
        }

        private static WebSocketFrame pongFrame(byte[] payload) {
            return new WebSocketFrame(PONG, payload);
        }

        private void writeTo(OutputStream output) throws IOException {
            byte finAndOpcode = (byte) (0x80 | opcode);
            output.write(finAndOpcode);

            int payloadLength = payloadData.length;
            if (payloadLength <= 125) {
                output.write(payloadLength);
            } else if (payloadLength <= 65535) {
                output.write(126);
                output.write((payloadLength >>> 8) & 0xFF);
                output.write(payloadLength & 0xFF);
            } else {
                output.write(127);
                for (int i = 7; i >= 0; i--) {
                    output.write((payloadLength >>> (8 * i)) & 0xFF);
                }
            }

            if (payloadLength > 0) {
                output.write(payloadData);
            }

            output.flush();
        }

        private static WebSocketFrame read(InputStream input) throws IOException {
            int firstByte = input.read();
            if (firstByte == -1) {
                return null;
            }

            int opcode = firstByte & 0x0F;
            int secondByte = input.read();
            if (secondByte == -1) {
                return null;
            }

            boolean masked = (secondByte & 0x80) != 0;
            long payloadLength = secondByte & 0x7F;

            if (payloadLength == 126) {
                payloadLength = ((long) input.read() << 8) | input.read();
            } else if (payloadLength == 127) {
                payloadLength = 0;
                for (int i = 0; i < 8; i++) {
                    payloadLength = (payloadLength << 8) | (input.read() & 0xFF);
                }
            }

            byte[] maskingKey = null;
            if (masked) {
                maskingKey = new byte[4];
                int read = input.read(maskingKey);
                if (read != 4) {
                    return null;
                }
            }

            byte[] payload = new byte[(int) payloadLength];
            int read = 0;
            while (read < payload.length) {
                int r = input.read(payload, read, payload.length - read);
                if (r == -1) {
                    return null;
                }
                read += r;
            }

            if (masked && maskingKey != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= maskingKey[i % 4];
                }
            }

            return new WebSocketFrame(opcode, payload);
        }
    }
}
