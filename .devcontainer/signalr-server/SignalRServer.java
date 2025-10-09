import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SignalRServer {
    private static final int PORT = 8080;
    private static final String RECORD_SEPARATOR = "\u001e";
    private static final ScheduledExecutorService EMITTER_EXECUTOR = Executors.newScheduledThreadPool(4);

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Java SignalR test server on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                Thread handlerThread = new Thread(new ConnectionHandler(socket));
                handlerThread.setDaemon(true);
                handlerThread.start();
            }
        }
    }

    private static class ConnectionHandler implements Runnable {
        private final Socket socket;
        private final Random random = new Random();
        private final Object sendLock = new Object();
        private volatile boolean socketOpen = true;

        ConnectionHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(0);
                InputStream input = socket.getInputStream();
                OutputStream output = socket.getOutputStream();

                HttpRequest request = HttpRequest.readFrom(input);
                if (request == null) {
                    closeSocket();
                    return;
                }

                String connectionHeader = request.headers.getOrDefault("connection", "");
                String upgradeHeader = request.headers.getOrDefault("upgrade", "");

                if ("GET".equals(request.method) && request.path.startsWith("/negotiate")) {
                    handleNegotiate(output);
                    closeSocket();
                } else if (connectionHeader.contains("upgrade") && "websocket".equals(upgradeHeader)) {
                    handleWebSocket(request, input, output);
                } else {
                    respondNotFound(output);
                    closeSocket();
                }
            } catch (IOException e) {
                closeSocket();
            }
        }

        private void handleNegotiate(OutputStream output) throws IOException {
            String connectionId = UUID.randomUUID().toString();
            String responseBody = "{" +
                    "\"connectionId\":\"" + connectionId + "\"," +
                    "\"availableTransports\":[{" +
                    "\"transport\":\"WebSockets\"," +
                    "\"transferFormats\":[\"Text\",\"Binary\"]" +
                    "}]," +
                    "\"negotiateVersion\":1" +
                    "}";
            byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + bodyBytes.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            synchronized (sendLock) {
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(bodyBytes);
                output.flush();
            }
        }

        private void handleWebSocket(HttpRequest request, InputStream input, OutputStream output) throws IOException {
            String key = request.headers.get("sec-websocket-key");
            if (key == null) {
                respondBadRequest(output);
                closeSocket();
                return;
            }

            String acceptKey;
            try {
                acceptKey = createAcceptKey(key);
            } catch (NoSuchAlgorithmException e) {
                respondServerError(output);
                closeSocket();
                return;
            }

            String handshakeResponse = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
            synchronized (sendLock) {
                output.write(handshakeResponse.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            boolean handshakeAcked = false;
            while (socketOpen) {
                WebSocketFrame frame = WebSocketFrame.read(input);
                if (frame == null) {
                    break;
                }

                switch (frame.opcode) {
                    case 0x1: // text frame
                        String payload = frame.getPayloadAsText();
                        if (!handshakeAcked) {
                            sendText(output, "{}" + RECORD_SEPARATOR);
                            handshakeAcked = true;
                            scheduleNextEvent(output);
                        } else if (payload != null && !payload.isEmpty()) {
                            // If client sends ping messages through SignalR (type 6), we simply ignore them.
                        }
                        break;
                    case 0x8: // close
                        sendCloseFrame(output, frame.payloadData);
                        socketOpen = false;
                        break;
                    case 0x9: // ping
                        sendPongFrame(output, frame.payloadData);
                        break;
                    default:
                        // Ignore other opcodes
                        break;
                }
            }

            closeSocket();
        }

        private void scheduleNextEvent(OutputStream output) {
            if (!socketOpen) {
                return;
            }
            int delay = 2 + random.nextInt(4);
            EMITTER_EXECUTOR.schedule(() -> {
                if (!socketOpen) {
                    return;
                }
                String eventName = random.nextBoolean() ? "EventA" : "EventB";
                String message = "{" +
                        "\"type\":1," +
                        "\"target\":\"" + eventName + "\"," +
                        "\"arguments\":[{" +
                        "\"message\":\"" + UUID.randomUUID() + "\"," +
                        "\"timestamp\":\"" + Instant.now() + "\"" +
                        "}]" +
                        "}" + RECORD_SEPARATOR;
                try {
                    sendText(output, message);
                } catch (IOException e) {
                    socketOpen = false;
                    closeSocket();
                    return;
                }
                scheduleNextEvent(output);
            }, delay, TimeUnit.SECONDS);
        }

        private void sendText(OutputStream output, String payload) throws IOException {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            sendFrame(output, 0x1, data);
        }

        private void sendCloseFrame(OutputStream output, byte[] payload) throws IOException {
            sendFrame(output, 0x8, payload != null ? payload : new byte[0]);
        }

        private void sendPongFrame(OutputStream output, byte[] payload) throws IOException {
            sendFrame(output, 0xA, payload != null ? payload : new byte[0]);
        }

        private void sendFrame(OutputStream output, int opcode, byte[] payload) throws IOException {
            if (!socketOpen) {
                return;
            }
            ByteBuffer buffer = ByteBuffer.allocate(2 + payloadLengthFieldSize(payload.length) + payload.length);
            buffer.put((byte) (0x80 | (opcode & 0x0F)));
            if (payload.length <= 125) {
                buffer.put((byte) payload.length);
            } else if (payload.length <= 0xFFFF) {
                buffer.put((byte) 126);
                buffer.putShort((short) payload.length);
            } else {
                buffer.put((byte) 127);
                buffer.putLong(payload.length);
            }
            buffer.put(payload);
            synchronized (sendLock) {
                output.write(buffer.array(), 0, buffer.position());
                output.flush();
            }
        }

        private int payloadLengthFieldSize(int payloadLength) {
            if (payloadLength <= 125) {
                return 0;
            }
            if (payloadLength <= 0xFFFF) {
                return 2;
            }
            return 8;
        }

        private void respondNotFound(OutputStream output) throws IOException {
            sendSimpleResponse(output, "404 Not Found", "text/plain", "Not Found");
        }

        private void respondBadRequest(OutputStream output) throws IOException {
            sendSimpleResponse(output, "400 Bad Request", "text/plain", "Bad Request");
        }

        private void respondServerError(OutputStream output) throws IOException {
            sendSimpleResponse(output, "500 Internal Server Error", "text/plain", "Internal Server Error");
        }

        private void sendSimpleResponse(OutputStream output, String status, String contentType, String body) throws IOException {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 " + status + "\r\n" +
                    "Content-Type: " + contentType + "\r\n" +
                    "Content-Length: " + data.length + "\r\n" +
                    "Connection: close\r\n\r\n";
            synchronized (sendLock) {
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(data);
                output.flush();
            }
        }

        private void closeSocket() {
            socketOpen = false;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class HttpRequest {
        final String method;
        final String path;
        final String version;
        final Map<String, String> headers;

        private HttpRequest(String method, String path, String version, Map<String, String> headers) {
            this.method = method;
            this.path = path;
            this.version = version;
            this.headers = headers;
        }

        static HttpRequest readFrom(InputStream input) throws IOException {
            String requestLine = readLine(input);
            if (requestLine == null || requestLine.isEmpty()) {
                return null;
            }
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 3) {
                return null;
            }

            Map<String, String> headers = new ConcurrentHashMap<>();
            String line;
            while ((line = readLine(input)) != null) {
                if (line.isEmpty()) {
                    break;
                }
                int colonIndex = line.indexOf(':');
                if (colonIndex > 0) {
                    String name = line.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(colonIndex + 1).trim().toLowerCase(Locale.ROOT);
                    headers.put(name, value);
                }
            }

            return new HttpRequest(parts[0], parts[1], parts[2], headers);
        }

        private static String readLine(InputStream input) throws IOException {
            StringBuilder builder = new StringBuilder();
            int previous = -1;
            while (true) {
                int current = input.read();
                if (current == -1) {
                    if (builder.length() == 0) {
                        return null;
                    }
                    break;
                }
                if (previous == '\r' && current == '\n') {
                    builder.setLength(builder.length() - 1);
                    break;
                }
                builder.append((char) current);
                previous = current;
            }
            return builder.toString();
        }
    }

    private static class WebSocketFrame {
        final int opcode;
        final byte[] payloadData;

        private WebSocketFrame(int opcode, byte[] payloadData) {
            this.opcode = opcode;
            this.payloadData = payloadData;
        }

        static WebSocketFrame read(InputStream input) throws IOException {
            int b1 = input.read();
            if (b1 == -1) {
                return null;
            }
            int b2 = input.read();
            if (b2 == -1) {
                return null;
            }

            int opcode = b1 & 0x0F;
            boolean masked = (b2 & 0x80) != 0;
            long payloadLength = b2 & 0x7F;

            if (payloadLength == 126) {
                byte[] extended = input.readNBytes(2);
                payloadLength = ((extended[0] & 0xFF) << 8) | (extended[1] & 0xFF);
            } else if (payloadLength == 127) {
                byte[] extended = input.readNBytes(8);
                payloadLength = 0;
                for (byte value : extended) {
                    payloadLength = (payloadLength << 8) | (value & 0xFF);
                }
            }

            byte[] maskingKey = masked ? input.readNBytes(4) : new byte[0];
            byte[] payload = input.readNBytes((int) payloadLength);

            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
                }
            }

            return new WebSocketFrame(opcode, payload);
        }

        String getPayloadAsText() {
            if (payloadData == null) {
                return null;
            }
            return new String(payloadData, StandardCharsets.UTF_8);
        }
    }

    private static String createAcceptKey(String key) throws NoSuchAlgorithmException {
        String concatenated = key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(concatenated.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
